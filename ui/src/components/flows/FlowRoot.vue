<template>
    <template v-if="ready">
        <flow-root-top-bar :route-info="routeInfo" :deleted="deleted" :is-allowed-edit="isAllowedEdit" :active-tab-name="activeTabName()" />
        <tabs
            @expand-subflow="updateExpandedSubflows"
            route-name="flows/update"
            ref="currentTab"
            :tabs="tabs"
        />
    </template>
</template>

<script>
    import Topology from "./Topology.vue";
    import FlowRevisions from "./FlowRevisions.vue";
    import FlowLogs from "./FlowLogs.vue";
    import FlowExecutions from "./FlowExecutions.vue";
    import RouteContext from "../../mixins/routeContext";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../Tabs.vue";
    import Overview from "./Overview.vue";
    import FlowDependencies from "./FlowDependencies.vue";
    import FlowMetrics from "./FlowMetrics.vue";
    import FlowEditor from "./FlowEditor.vue";
    import FlowTriggers from "./FlowTriggers.vue";
    import {apiUrl} from "override/utils/route";
    import FlowRootTopBar from "./FlowRootTopBar.vue";

    export default {
        mixins: [RouteContext],
        components: {
            Tabs,
            FlowRootTopBar,
        },
        data() {
            return {
                tabIndex: undefined,
                previousFlow: undefined,
                dependenciesCount: undefined,
                expandedSubflows: [],
                deleted: false,
            };
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    this.load();
                }
            },
            guidedProperties: {
                deep: true,
                immediate: true,
                handler: function (newValue) {
                    if (newValue?.manuallyContinue) {
                        setTimeout(() => {
                            this.$tours["guidedTour"].nextStep();
                            this.$store.commit("core/setGuidedProperties", {manuallyContinue: false});
                        }, 500);
                    }
                },
            },
        },
        created() {
            this.load();
        },
        methods: {
            load() {
                if (
                    this.flow === undefined ||
                    this.previousFlow !== this.flowKey()
                ) {
                    const query = {...this.$route.query, allowDeleted: true};
                    return this.$store
                        .dispatch("flow/loadFlow", {
                            ...this.$route.params,
                            ...query,
                        })
                        .then(() => {
                            if (this.flow) {
                                this.deleted = this.flow.deleted;
                                this.previousFlow = this.flowKey();
                                this.$store.dispatch("flow/loadGraph", {
                                    flow: this.flow,
                                });
                                this.$http
                                    .get(
                                        `${apiUrl(this.$store)}/flows/${this.flow.namespace}/${this.flow.id}/dependencies`,
                                    )
                                    .then((response) => {
                                        this.dependenciesCount =
                                            response.data && response.data.nodes
                                                ? [
                                                    ...new Set(
                                                        response.data.nodes.map(
                                                            (r) => r.uid,
                                                        ),
                                                    ),
                                                ].length
                                                : 0;
                                    });
                            }
                        });
                }
            },
            flowKey() {
                return this.$route.params.namespace + "/" + this.$route.params.id;
            },
            getTabs() {
                let tabs = [
                    {
                        name: undefined,
                        component: Topology,
                        title: this.$t("topology"),
                        props: {
                            isReadOnly: true,
                            expandedSubflows: this.expandedSubflows,
                        },
                    },
                ];

                if (this.user.hasAny(permission.EXECUTION)) {
                    tabs[0].name = "topology";

                    tabs = [
                        {
                            name: undefined,
                            component: Overview,
                            title: this.$t("overview"),
                        },
                    ].concat(tabs);
                }

                if (
                    this.user &&
                    this.flow &&
                    this.user.isAllowed(
                        permission.EXECUTION,
                        action.READ,
                        this.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "executions",
                        component: FlowExecutions,
                        title: this.$t("executions"),
                    });
                }

                if (
                    this.user &&
                    this.flow &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "editor",
                        component: FlowEditor,
                        title: this.$t("editor"),
                        containerClass: "full-container",
                        props: {
                            expandedSubflows: this.expandedSubflows,
                            isReadOnly: this.deleted || !this.isAllowedEdit,
                        },
                    });
                }

                if (
                    this.user &&
                    this.flow &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "revisions",
                        component: FlowRevisions,
                        containerClass: "container full-height",
                        title: this.$t("revisions"),
                    });
                }

                if (
                    this.user &&
                    this.flow &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "triggers",
                        component: FlowTriggers,
                        title: this.$t("triggers"),
                        disabled: !this.flow.triggers,
                    });
                }

                if (
                    this.user &&
                    this.flow &&
                    this.user.isAllowed(
                        permission.EXECUTION,
                        action.READ,
                        this.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "logs",
                        component: FlowLogs,
                        title: this.$t("logs"),
                    });
                }

                if (
                    this.user &&
                    this.flow &&
                    this.user.isAllowed(
                        permission.EXECUTION,
                        action.READ,
                        this.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "metrics",
                        component: FlowMetrics,
                        title: this.$t("metrics"),
                    });
                }
                if (
                    this.user &&
                    this.flow &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "dependencies",
                        component: FlowDependencies,
                        title: this.$t("dependencies"),
                        count: this.dependenciesCount,
                    });
                }

                tabs.push(                    {
                    name: "auditlogs",
                    title: this.$t("auditlogs"),
                    locked: true
                });

                return tabs;
            },
            updateExpandedSubflows(expandedSubflows) {
                this.expandedSubflows = expandedSubflows;
            },
            activeTabName() {
                return this.$refs.currentTab?.activeTab?.name ?? "home";
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            ...mapState("auth", ["user"]),
            ...mapState("core", ["guidedProperties"]),
            routeInfo() {
                return {
                    title: this.$route.params.id,
                    breadcrumb: [
                        {
                            label: this.$t("flows"),
                            link: {
                                name: "flows/list",
                            },
                        },
                        {
                            label: this.$route.params.namespace,
                            link: {
                                name: "flows/list",
                                query: {
                                    namespace: this.$route.params.namespace,
                                },
                            },
                        },
                    ],
                };
            },
            tabs() {
                return this.getTabs();
            },
            ready() {
                return this.user !== undefined && this.flow !== undefined;
            },
            isAllowedEdit() {
                if (!this.flow || !this.user) {
                    return false;
                }

                return this.user.isAllowed(
                    permission.FLOW,
                    action.UPDATE,
                    this.flow.namespace,
                );
            },
        },
        unmounted() {
            this.$store.commit("flow/setFlow", undefined);
            this.$store.commit("flow/setFlowGraph", undefined);
        },
    };
</script>
<style lang="scss" scoped>
.gray-700 {
    color: var(--bs-secondary-color);
}
.body-color {
    color: var(--bs-body-color);
}
</style>
