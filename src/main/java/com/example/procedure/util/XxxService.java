package com.example.procedure.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class XxxService {

    private final ObjectMapper objectMapper;

    public XxxService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 用的时候：
    // DecryptResponse resp = objectMapper.readValue(respJson, DecryptResponse.class);
}
