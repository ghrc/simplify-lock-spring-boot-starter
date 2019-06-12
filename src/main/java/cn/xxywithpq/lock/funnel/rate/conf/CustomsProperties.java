package cn.xxywithpq.lock.funnel.rate.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "simplify.lock")
public class CustomsProperties {
    private String namespace;

    public String getNamespace() {
        if (StringUtils.isEmpty(namespace)) {
            return "simplify-lock";
        }
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}