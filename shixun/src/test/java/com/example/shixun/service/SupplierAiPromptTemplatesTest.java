package com.example.shixun.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierAiPromptTemplatesTest {
    @Test
    void toolRouterPrompt_containsSearchSuppliersSchemaAndSqlGuardrail() {
        assertThat(SupplierAiPromptTemplates.TOOL_ROUTER_SYSTEM_PROMPT)
                .contains("search_suppliers")
                .contains("get_supplier_statistics")
                .contains("region")
                .contains("bank_name")
                .contains("is_count_only")
                .contains("禁止生成 SQL")
                .contains("数据唯一来源原则")
                .contains("禁止外部知识注入");
    }

    @Test
    void answerPrompt_requiresToolGroundedSmallWindowAnswer() {
        assertThat(SupplierAiPromptTemplates.ANSWER_SYSTEM_PROMPT)
                .contains("必须基于工具结果回答")
                .contains("纯文字短段落")
                .contains("付款前请务必人工二次核验")
                .contains("工具返回 JSON")
                .contains("不得输出工具返回中没有的地理大区");
    }
}
