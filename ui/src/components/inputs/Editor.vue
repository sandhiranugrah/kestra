<template>
    <div class="ks-editor edit-flow-editor">
        <nav v-if="original === undefined && navbar" class="top-nav">
            <slot name="nav">
                <div class="text-nowrap">
                    <el-button-group>
                        <el-tooltip
                            effect="light"
                            :content="$t('Fold content lines')"
                            :persistent="false"
                            transition=""
                            :hide-after="0"
                        >
                            <el-button :icon="icon.UnfoldLessHorizontal" @click="autoFold(true)" size="small" />
                        </el-tooltip>
                        <el-tooltip
                            effect="light"
                            :content="$t('Unfold content lines')"
                            :persistent="false"
                            transition=""
                            :hide-after="0"
                        >
                            <el-button :icon="icon.UnfoldMoreHorizontal" @click="unfoldAll" size="small" />
                        </el-tooltip>
                    </el-button-group>
                    <slot name="extends-navbar" />
                </div>
            </slot>
        </nav>

        <div class="editor-container" ref="container" :class="containerClass">
            <div ref="editorContainer" class="editor-wrapper position-relative">
                <monaco-editor
                    ref="monacoEditor"
                    :theme="themeComputed"
                    :value="modelValue"
                    :options="options"
                    :diff-editor="original !== undefined"
                    :original="original"
                    @change="onInput"
                    @editor-did-mount="editorDidMount"
                    :language="lang"
                    :extension="extension"
                    :schema-type="schemaType"
                    :input="input"
                />
                <div
                    v-show="showPlaceholder"
                    class="placeholder"
                    @click="onPlaceholderClick"
                >
                    {{ placeholder }}
                </div>
                <div class="position-absolute bottom-right">
                    <slot name="buttons" />
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import {defineAsyncComponent, shallowRef} from "vue"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue";
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue";
    import Help from "vue-material-design-icons/Help.vue";
    import {mapGetters} from "vuex";
    import BookMultipleOutline from "vue-material-design-icons/BookMultipleOutline.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import {TabFocus} from "monaco-editor/esm/vs/editor/browser/config/tabFocus.js";

    const MonacoEditor = defineAsyncComponent(() =>
        import("./MonacoEditor.vue")
    )

    export default {
        props: {
            modelValue: {type: String, default: ""},
            original: {type: String, default: undefined},
            lang: {type: String, default: undefined},
            extension: {type: String, default: undefined},
            schemaType: {type: String, default: undefined},
            navbar: {type: Boolean, default: true},
            input: {type: Boolean, default: false},
            fullHeight: {type: Boolean, default: true},
            customHeight: {type: Number, default: 7},
            theme: {type: String, default: undefined},
            placeholder: {type: [String, Number], default: ""},
            diffSideBySide: {type: Boolean, default: true},
            readOnly: {type: Boolean, default: false},
            lineNumbers: {type: Boolean, default: undefined},
            minimap: {type: Boolean, default: false},
            creating: {type: Boolean, default: false}
        },
        components: {
            MonacoEditor,
        },
        emits: ["save", "execute", "focusout", "tab", "update:modelValue", "cursor"],
        editor: undefined,
        data() {
            return {
                focus: false,
                icon: {
                    UnfoldLessHorizontal: shallowRef(UnfoldLessHorizontal),
                    UnfoldMoreHorizontal: shallowRef(UnfoldMoreHorizontal),
                    Help: shallowRef(Help),
                    BookMultipleOutline: shallowRef(BookMultipleOutline),
                    Close: shallowRef(Close)
                },
                editorDocumentation: undefined,
                plugin: undefined,
                taskType: undefined,
            };
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("flow", ["flowValidation"]),
            themeComputed() {
                const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

                return this.theme ? this.theme : (localStorage.getItem("editorTheme") || (darkTheme ? "dark" : "vs"))
            },
            containerClass() {
                return [
                    !this.input ? "" : "single-line",
                    "theme-" + this.themeComputed,
                    this.themeComputed === "dark" ? "custom-dark-vs-theme" : ""
                ]
            },
            showPlaceholder() {
                return this.input === true && !this.focus &&
                    (!Object.hasOwn(this, "editor") || this.editor === undefined || !(this.editor.getValue() !== undefined && this.editor.getValue() !== ""));
            },
            options() {
                const options = {}

                if (this.input) {
                    options.lineNumbers = "off"
                    options.folding = false;
                    options.renderLineHighlight = "none"
                    options.wordBasedSuggestions = false;
                    options.occurrencesHighlight = false
                    options.hideCursorInOverviewRuler = true
                    options.overviewRulerBorder = false
                    options.overviewRulerLanes = 0
                    options.lineNumbersMinChars = 0;
                    options.fontSize = 13;
                    options.minimap = {
                        enabled: false
                    }
                    options.scrollBeyondLastColumn = 0;
                    options.overviewRulerLanes = 0;
                    options.scrollbar = {
                        vertical: "hidden",
                        horizontal: "hidden",
                        alwaysConsumeMouseWheel: false,
                        handleMouseWheel: false,
                        horizontalScrollbarSize: 0,
                        verticalScrollbarSize: 0,
                        useShadows: false,
                    };
                    options.find = {
                        addExtraSpaceOnTop: false,
                        autoFindInSelection: "never",
                        seedSearchStringFromSelection: false,
                    }
                    options.contextmenu = false;
                    options.lineDecorationsWidth = 0;
                } else {
                    options.scrollbar = {
                        vertical: this.original !== undefined ? "hidden" : "auto",
                        verticalScrollbarSize: this.original !== undefined ? 0 : 10,
                        alwaysConsumeMouseWheel: false,
                    };
                    options.renderSideBySide = this.diffSideBySide
                }

                if (this.minimap === false) {
                    options.minimap = {
                        enabled: false

                    }
                }

                if (this.readOnly) {
                    options.readOnly = true
                }

                options.wordWrap = true
                options.automaticLayout = true;

                return {
                    ...{
                        tabSize: 2,
                        fontFamily: localStorage.getItem("editorFontFamily") ? localStorage.getItem("editorFontFamily") : "'Source Code Pro', monospace",
                        fontSize: localStorage.getItem("editorFontSize") ? parseInt(localStorage.getItem("editorFontSize")) : 12,
                        showFoldingControls: "always",
                        scrollBeyondLastLine: false,
                        roundedSelection: false,
                    },
                    ...options
                };
            }
        },
        created() {
            this.editorDocumentation = localStorage.getItem("editorDocumentation") !== "false" && this.navbar;
        },
        methods: {
            editorDidMount(editor) {
                // avoid double import of monaco editor, use a reference
                const KeyCode = this.$refs.monacoEditor.monaco.KeyCode;
                const KeyMod = this.$refs.monacoEditor.monaco.KeyMod;

                this.editor = editor;

                this.decorations = this.editor.createDecorationsCollection();

                if (!this.original) {
                    this.editor.onDidBlurEditorWidget?.(() => {
                        this.$emit("focusout", editor.getValue());
                        this.focus = false;
                    })

                    this.editor.onDidFocusEditorText?.(() => {
                        this.focus = true;
                    })

                    this.$refs.monacoEditor.focus();
                }

                if (!this.readOnly) {
                    this.editor.addAction({
                        id: "kestra-save",
                        label: "Save",
                        keybindings: [
                            KeyMod.CtrlCmd | KeyCode.KeyS,
                        ],
                        contextMenuGroupId: "navigation",
                        contextMenuOrder: 1.5,
                        run: (ed) => {
                            this.$emit("save", ed.getValue())
                        }
                    });
                }
                else {
                    if (this.lang === "json") {
                        editor.getAction("editor.action.formatDocument").run()
                    }
                }

                this.editor.addAction({
                    id: "kestra-execute",
                    label: "Execute the flow",
                    keybindings: [
                        KeyMod.CtrlCmd | KeyCode.KeyE,
                    ],
                    contextMenuGroupId: "navigation",
                    contextMenuOrder: 1.5,
                    run: (ed) => {
                        this.$emit("execute", ed.getValue())
                    }
                });

                // TabFocus is global to all editor so revert the behavior on non inputs
                this.editor.onDidFocusEditorText?.(() => {
                    TabFocus.setTabFocusMode(this.input);
                })

                if (this.input) {
                    this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyF, () => {});
                    this.editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyH, () => {});
                    this.editor.addCommand(KeyCode.F1, () => {});
                }

                if (this.original === undefined && this.navbar && this.fullHeight) {
                    this.editor.addAction({
                        id: "fold-multiline",
                        label: "Fold All Multi Lines",
                        keybindings: [
                            KeyCode.F10,
                        ],
                        contextMenuGroupId: "fold",
                        contextMenuOrder: 1.5,
                        run: (ed) => {
                            const foldingContrib = ed.getContribution("editor.contrib.folding");
                            foldingContrib.getFoldingModel().then(foldingModel => {

                                let editorModel = foldingModel.textModel;
                                let regions = foldingModel.regions;
                                let toToggle = [];
                                for (let i = regions.length - 1; i >= 0; i--) {
                                    if (regions.isCollapsed(i) === false) {
                                        let startLineNumber = regions.getStartLineNumber(i);

                                        if (editorModel.getLineContent(startLineNumber).trim().endsWith("|")) {
                                            toToggle.push(regions.toRegion(i));
                                        }
                                    }
                                }
                                foldingModel.toggleCollapseState(toToggle);
                            });

                            return null;
                        }
                    });

                    if (localStorage.getItem("autofoldTextEditor") === "1") {
                        this.autoFold(true);
                    }
                }

                if (this.original !== undefined) {
                    this.editor.updateOptions({readOnly: true})
                }

                if (!this.fullHeight) {
                    editor.onDidContentSizeChange(e => {
                        this.$refs.container.style.height = (e.contentHeight + this.customHeight) + "px";
                    });
                }

                if (!this.original) {
                    this.editor.onDidContentSizeChange(_ => {
                        if (this.guidedProperties.monacoRange) {
                            editor.revealLine(this.guidedProperties.monacoRange.endLineNumber);
                            const decorationsToAdd = [];
                            decorationsToAdd.push({
                                range: this.guidedProperties.monacoRange,
                                options: {
                                    isWholeLine: true,
                                    inlineClassName: "highlight-text"
                                },
                                className: "highlight-text",
                            });
                            if (this.guidedProperties.monacoDisableRange) {
                                decorationsToAdd.push({
                                    range: this.guidedProperties.monacoDisableRange,
                                    options: {
                                        isWholeLine: true,
                                        inlineClassName: "disable-text"
                                    },
                                    className: "disable-text",
                                });
                            }

                            this.decorations.set(decorationsToAdd);
                        } else {
                            this.highlightPebble();
                        }
                    });

                    this.editor.onDidChangeCursorPosition?.(() => {
                        let position = this.editor.getPosition();
                        let model = this.editor.getModel();
                        clearTimeout(this.lastTimeout);
                        this.lastTimeout = setTimeout(() => {
                            this.$emit("cursor", {position: position, model: model})
                        }, 100);
                        this.highlightPebble();
                    });
                }
            },
            autoFold(autoFold) {
                if (autoFold) {
                    this.editor.trigger("fold", "fold-multiline");
                }
            },
            unfoldAll() {
                this.editor.trigger("unfold", "editor.unfoldAll");
            },
            onInput(value) {
                this.$emit("update:modelValue", value);
            },
            onPlaceholderClick() {
                this.editor.layout()
                this.editor.focus()
            },
            highlightPebble() {
                // Highlight code that match pebble content
                let model = this.editor?.getModel?.();
                let text = model?.getValue?.();
                let regex = new RegExp("\\{\\{(.+?)}}", "g");
                let match;
                const decorationsToAdd = [];
                while ((match = regex.exec(text)) !== null) {
                    let startPos = model.getPositionAt(match.index);
                    let endPos = model.getPositionAt(match.index + match[0].length);
                    decorationsToAdd.push({
                        range: {
                            startLineNumber: startPos.lineNumber,
                            startColumn: startPos.column,
                            endLineNumber: endPos.lineNumber,
                            endColumn: endPos.column
                        },
                        options: {
                            inlineClassName: "highlight-pebble"
                        }
                    });
                }
                this.decorations.set(decorationsToAdd);
            }
        },
    };
</script>

<style lang="scss">
    @import "../../styles/layout/root-dark.scss";

    :not(.el-drawer__body) > .ks-editor {
        flex-direction: column;
        height: 100%;
    }

    .el-drawer__body .ks-editor {
        flex: 1;
    }

    .el-dialog__body .ks-editor {
        display: flex;
        width: 100%;
    }

    .el-dialog__body .el-form {
        width: 100%;
    }

    .ks-editor {
        display: flex;

        .top-nav {
            background-color: var(--bs-white);
            padding: calc(var(--spacer) / 2);
            border-radius: var(--bs-border-radius-lg);
            border-bottom-left-radius: 0;
            border-bottom-right-radius: 0;

            html.dark & {
                background-color: var(--bs-gray-100);
            }
        }

        .editor-container {
            display: flex;
            flex-grow: 1;

            &.single-line {
                min-height: var(--el-component-size);
                padding: 1px 11px;
                background-color: var(--el-input-bg-color, var(--el-fill-color-blank));
                border-radius: var(--el-input-border-radius, var(--el-border-radius-base));
                transition: var(--el-transition-box-shadow);
                box-shadow: 0 0 0 1px var(--bs-border-color) inset;
                padding-top: 7px;

                html.dark & {
                    background-color: var(--bs-gray-100);
                }
            }

            .placeholder {
                position: absolute;
                top: -3px;
                overflow: hidden;
                padding-left: inherit;
                padding-right: inherit;
                cursor: text;
                user-select: none;
                color: var(--el-text-color-placeholder);
            }

            .editor-wrapper {
                min-width: 75%;
                width: 100%;

                .monaco-hover-content {
                    h4 {
                        font-size: var(--font-size-base);
                        font-weight: bold;
                        line-height: var(--bs-body-line-height);
                    }

                    p {
                        margin-bottom: calc(var(--spacer) / 2);

                        &:last-child {
                            display: none;
                        }
                    }

                    *:nth-last-child(2n) {
                        margin-bottom: 0;
                    }
                }
            }

            .bottom-right {
                bottom: 0px;
                right: 0px;

                ul {
                    display: flex;
                    list-style: none;
                    padding: 0;
                    margin: 0;
                    //gap: calc(var(--spacer) / 2);
                }
            }
        }
    }

    .custom-dark-vs-theme {
        .monaco-editor, .monaco-editor-background {
            background-color: $input-bg;
            --vscode-editor-background: $input-bg;
            --vscode-breadcrumb-background: $input-bg;
            --vscode-editorGutter-background: $input-bg;
        }

        .monaco-editor .margin {
            background-color: $input-bg;
        }
    }

    .highlight-text {
        cursor: pointer;
        font-weight: 700;
        box-shadow: 0 19px 44px rgba(157, 29, 236, 0.31);

        html.dark & {
            background-color: rgba(255, 255, 255, 0.2);
        }
    }

    .highlight-pebble {
        color: #977100 !important;

        html.dark & {
            color: #ffca16 !important;
        }
    }

    .disable-text {
        color: grey !important;
    }

    div.img {
        min-height: 130px;
        height: 100%;

        &.get-started {
            background: url("../../assets/onboarding/onboarding-doc-light.svg") no-repeat center;

            html.dark & {
                background: url("../../assets/onboarding/onboarding-doc-dark.svg") no-repeat center;
            }
        }
    }

</style>
