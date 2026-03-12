package com.izylife.ssi.dto.oidc4vci;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisplayEntry {

    @JsonProperty("name")
    private String name;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("description")
    private String description;

    public DisplayEntry(String name, String locale) {
        this.name = name;
        this.locale = locale;
    }

    public DisplayEntry(String name, String locale, String description) {
        this.name = name;
        this.locale = locale;
        this.description = description;
    }
}
