package org.cschoell.postman.converter.postmanconverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JDefinedClass;
import lombok.Data;
import org.jsonschema2pojo.AbstractAnnotator;

public class LombokAnnotator extends AbstractAnnotator {

    @Override
    public void typeInfo(JDefinedClass clazz, JsonNode schema) {
        super.typeInfo(clazz, schema);
        clazz.annotate(Data.class);
    }
}
