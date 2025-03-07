<template>
    <template v-if="ready">
        <execution-root-top-bar :route-info="routeInfo" />
        <tabs
            :route-name="$route.params && $route.params.id ? 'executions/update': ''"
            @follow="follow"
            :tabs="tabs"
        />
    </template>
    <div v-else class="full-space" v-loading="!ready" />
</template>

<script>
    import Gantt from "./Gantt.vue";
    import Overview from "./Overview.vue";
    import Logs from "./Logs.vue";
    import Topology from "./Topology.vue";
    import ExecutionOutput from "./ExecutionOutput.vue";
    import RouteContext from "../../mixins/routeContext";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../../components/Tabs.vue";
    import ExecutionRootTopBar from "./ExecutionRootTopBar.vue";

    import ExecutionMetric from "./ExecutionMetric.vue";
    import throttle from "lodash/throttle";

    export default {
        mixins: [RouteContext],
        components: {
            Tabs,
            ExecutionRootTopBar,
        },
        data() {
            return {
                sse: undefined,
                previousExecutionId: undefined,
                throttledExecutionUpdate: throttle(function (executionEvent) {
                    let execution = JSON.parse(executionEvent.data);

                    if ((!this.flow ||
                        execution.flowId !== this.flow.id ||
                        execution.namespace !== this.flow.namespace ||
                        execution.flowRevision !== this.flow.revision)
                    ) {
                        this.$store.dispatch(
                            "execution/loadFlowForExecutionByExecutionId",
                            {
                                id: execution.id,
                            }
                        );
                    }

                    this.$store.commit("execution/setExecution", execution);
                }, 500)
            };
        },
        created() {
            this.follow();
            window.addEventListener("popstate", this.follow)
        },
        mounted() {
            this.previousExecutionId = this.$route.params.id
        },
        watch: {
            $route(newValue, oldValue) {
                this.$store.commit("execution/setTaskRun", undefined);
                if (oldValue.name === newValue.name && this.previousExecutionId !== this.$route.params.id) {
                    this.follow()
                }
            },
        },
        methods: {
            follow() {
                this.closeSSE();
                this.previousExecutionId = this.$route.params.id;
                this.$store
                    .dispatch("execution/followExecution", this.$route.params)
                    .then(sse => {
                        this.sse = sse;
                        this.sse.onmessage = (executionEvent) => {
                            const isEnd = executionEvent && executionEvent.lastEventId === "end";
                            if (isEnd) {
                                this.closeSSE();
                            }
                            this.throttledExecutionUpdate(executionEvent);
                            if (isEnd) {
                                this.throttledExecutionUpdate.flush();
                            }
                        }
                        // sse.onerror doesnt return the details of the error
                        // but as our emitter can only throw an error on 404
                        // we can safely assume that the error
                        this.sse.onerror = () => {
                            this.$store.dispatch("core/showMessage", {
                                variant: "error",
                                title: this.$t("error"),
                                message: this.$t("errors.404.flow or execution"),
                            });
                        }
                    });
            },
            closeSSE() {
                if (this.sse) {
                    this.sse.close();
                    this.sse = undefined;
                }
            },
            getTabs() {
                const title = title => this.$t(title);
                return [
                    {
                        name: undefined,
                        component: Overview,
                        title: title("overview"),
                    },
                    {
                        name: "gantt",
                        component: Gantt,
                        title: title("gantt")
                    },
                    {
                        name: "logs",
                        component: Logs,
                        title: title("logs")
                    },
                    {
                        name: "topology",
                        component: Topology,
                        title: title("topology")
                    },
                    {
                        name: "outputs",
                        component: ExecutionOutput,
                        title: title("outputs")
                    },
                    {
                        name: "metrics",
                        component: ExecutionMetric,
                        title: title("metrics")
                    },
                    {
                        name: "auditlogs",
                        title: title("auditlogs"),
                        locked: true
                    }
                ];
            },
        },
        computed: {
            // ...mapState("flow", ["flow", "revisions"]),
            ...mapState("execution", ["execution", "flow"]),
            ...mapState("auth", ["user"]),
            tabs() {
                return this.getTabs();
            },
            routeInfo() {
                const ns = this.$route.params.namespace;
                const flowId = this.$route.params.flowId;

                if (!ns || !flowId) {
                    return {};
                }

                return {
                    title: this.$route.params.id,
                    breadcrumb: [
                        {
                            label: this.$t("flows"),
                            link: {
                                name: "flows/list",
                                query: {
                                    namespace: ns
                                }
                            }
                        },
                        {
                            label: `${ns}.${flowId}`,
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: ns,
                                    id: flowId
                                }
                            }
                        },
                        {
                            label: this.$t("executions"),
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: ns,
                                    id: flowId,
                                    tab: "executions"
                                }
                            }
                        }
                    ]
                };
            },
            isAllowedTrigger() {
                return this.user && this.execution && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.execution.namespace);
            },
            isAllowedEdit() {
                return this.user && this.execution && this.user.isAllowed(permission.FLOW, action.UPDATE, this.execution.namespace);
            },
            canDelete() {
                return this.user && this.execution && this.user.isAllowed(permission.EXECUTION, action.DELETE, this.execution.namespace);
            },
            ready() {
                return this.execution !== undefined;
            }
        },
        beforeUnmount() {
            this.closeSSE();
            window.removeEventListener("popstate", this.follow)
            this.$store.commit("execution/setExecution", undefined);
            this.$store.commit("flow/setFlow", undefined);
            this.$store.commit("flow/setFlowGraph", undefined);
        }
    };
</script>
<style lang="scss" scoped>
    .full-space {
        flex: 1 1 auto;
    }
</style>