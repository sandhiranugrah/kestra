package io.kestra.core.schedulers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@MicronautTest(transactional = false, rebuildContext = true) // rebuild context to lower possible flaky tests
abstract public class AbstractSchedulerTest {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    protected static Flow createFlow(List<AbstractTrigger> triggers) {
        return createFlow(triggers, null);
    }

    protected static Flow createFlow(List<AbstractTrigger> triggers, List<PluginDefault> list) {
        Flow.FlowBuilder<?, ?> flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .inputs(List.of(
                StringInput.builder()
                    .type(Type.STRING)
                    .id("testInputs")
                    .required(false)
                    .defaults("test")
                    .build(),
                StringInput.builder()
                    .type(Type.STRING)
                    .id("def")
                    .required(false)
                    .defaults("awesome")
                    .build()
            ))
            .revision(1)
            .labels(
                List.of(
                    new Label("flow-label-1", "flow-label-1"),
                    new Label("flow-label-2", "flow-label-2")
                )
            )
            .triggers(triggers)
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format("{{ inputs.testInputs }}")
                .build()));

        if (list != null) {
            flow.pluginDefaults(list);
        }

        return flow
            .build();
    }

    protected static int COUNTER = 0;

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class UnitTest extends AbstractTrigger implements PollingTriggerInterface {
        @Builder.Default
        private final Duration interval = Duration.ofSeconds(2);

        private String defaultInjected;

        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws InterruptedException {
            COUNTER++;

            if (COUNTER % 2 == 0) {
                Thread.sleep(4000);

                return Optional.empty();
            } else {
                Execution execution = Execution.builder()
                    .id(IdUtils.create())
                    .namespace(context.getNamespace())
                    .flowId(context.getFlowId())
                    .flowRevision(context.getFlowRevision())
                    .state(new State())
                    .trigger(ExecutionTrigger.builder()
                        .id(this.getId())
                        .type(this.getType())
                        .variables(ImmutableMap.of(
                            "counter", COUNTER,
                            "defaultInjected", defaultInjected == null ? "ko" : defaultInjected
                        ))
                        .build()
                    )
                    .build();

                return Optional.of(execution);
            }
        }
    }
}
