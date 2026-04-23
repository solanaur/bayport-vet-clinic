package com.bayport.service;

import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class EmailTemplateService {

    public String applyTemplate(String template, Map<String, String> values) {
        String output = template;
        for (String key : values.keySet()) {
            output = output.replace("{{" + key + "}}", values.get(key));
        }
        return output;
    }
}
