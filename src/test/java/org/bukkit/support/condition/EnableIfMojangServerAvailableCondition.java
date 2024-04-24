package org.bukkit.support.condition;

import com.mojang.authlib.yggdrasil.YggdrasilEnvironment;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class EnableIfMojangServerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
        try {
            InetAddress address = InetAddress.getByName(YggdrasilEnvironment.PROD.getEnvironment().servicesHost());

            if (!address.isReachable((int) TimeUnit.SECONDS.toMillis(1))) {
                return ConditionEvaluationResult.disabled("Mojang server is not available");
            }

            return ConditionEvaluationResult.enabled("Mojang server available");
        } catch (Exception e) {
            return ConditionEvaluationResult.disabled(e.getMessage());
        }
    }
}
