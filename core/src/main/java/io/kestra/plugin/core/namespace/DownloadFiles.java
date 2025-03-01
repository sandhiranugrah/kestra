package io.kestra.plugin.core.namespace;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.NamespaceFilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.Rethrow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.commons.nullanalysis.NotNull;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.kestra.core.utils.PathUtil.checkLeadingSlash;

@Slf4j
@SuperBuilder
@Getter
@NoArgsConstructor
@Schema(
    title = "Download one or multiple files from your namespace files."
)
@Plugin(
    examples = {
        @Example(
            title = "Download namespace files.",
            full = true,
            code = {
                """
                id: namespace-file-download
                namespace: io.kestra.tests
                tasks:
                    - id: download
                      type: io.kestra.plugin.core.namespace.DownloadFiles
                      namespace: tutorial
                      files:
                      - **input.txt"
                """
            }
        ),
        @Example(
            title = "Download all namespace files.",
            full = true,
            code = {
                """
                id: namespace-file-download
                namespace: io.kestra.tests
                tasks:
                    - id: download
                      type: io.kestra.plugin.core.namespace.DownloadFiles
                      namespace: tutorial
                      files:
                      - **"
                """
            }
        )
    }
)
public class DownloadFiles extends Task implements RunnableTask<DownloadFiles.Output> {
    @NotNull
    @Schema(
        title = "The namespace where you want to apply the action."
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotNull
    @NotEmpty
    @Schema(
        title = "A file or list of files from the given namespace.",
        description = "String or List of String, each string can either be a regex using glob pattern or a URI.",
        anyOf = {List.class, String.class}
    )
    @PluginProperty(dynamic = true)
    private Object files;


    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        String renderedNamespace = runContext.render(namespace);
        // Check if namespace is allowed
        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        FlowService flowService = runContext.getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(flowInfo.tenantId(), renderedNamespace, flowInfo.tenantId(), flowInfo.namespace());

        NamespaceFilesService namespaceFilesService = runContext.getApplicationContext().getBean(NamespaceFilesService.class);

        List<String> renderedFiles;
        if (files instanceof String filesString) {
            renderedFiles = List.of(runContext.render(filesString));
        } else if (files instanceof List<?> filesList) {
            renderedFiles = runContext.render((List<String>) filesList);
        } else {
            throw new IllegalArgumentException("Files must be a String or a list of String");
        }

        List<PathMatcher> patterns = renderedFiles.stream().map(reg -> FileSystems.getDefault().getPathMatcher("glob:" + checkLeadingSlash(reg))).toList();
        Map<String, URI> downloaded = new HashMap<>();

        namespaceFilesService.recursiveList(flowInfo.tenantId(), renderedNamespace, null).forEach(Rethrow.throwConsumer(uri -> {
            if (patterns.stream().anyMatch(p -> p.matches(Path.of(uri.getPath())))) {
                try (InputStream inputStream = namespaceFilesService.content(flowInfo.tenantId(), renderedNamespace, uri)) {
                    downloaded.put(uri.getPath(), runContext.storage().putFile(inputStream, uri.getPath()));
                    logger.debug(String.format("Downloaded %s", uri));
                }
            }
        }));
        runContext.metric(Counter.of("downloaded", downloaded.size()));
        return Output.builder().files(downloaded).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Downloaded files.",
            description = "Return a map containing the file path as a key and the URI as a value."
        )
        private final Map<String, URI> files;
    }

}
