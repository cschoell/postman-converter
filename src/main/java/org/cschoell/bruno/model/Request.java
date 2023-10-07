package org.cschoell.bruno.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@JsonPropertyOrder({
        "url",
        "body",
        "auth"
})
public class Request extends BrunoModelBase {

    private String method;
    private String url;
    private BodyType body;
    private String auth;

    @Override
    public String getComponentRootName() {
        return StringUtils.lowerCase(getMethod());
    }

    @JsonIgnore
    public String getMethod() {
        return method;
    }

    @JsonIgnore
    public void setMethod(String method) {
        this.method = StringUtils.lowerCase(method);
    }

    public void setAuth(String auth) {
        this.auth = StringUtils.lowerCase(auth);
    }
}
