package com.wangyue.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.wangyue.backend.dto.RecognizedQuestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class WordDocumentServiceTest {

    @Test
    void keepsTheOriginalStemForStandardNumberedWordQuestions() {
        String source = """
            # 单项选择题
            1. 下级组织认为上级决定不符合本地实际，可（）
            A.请求改变 B.不予执行 C.暂缓执行 D.变通执行
            【答案】A
            """;

        List<RecognizedQuestion> questions = new WordDocumentService()
            .parseStructuredQuestions(source)
            .questions();

        assertEquals(1, questions.size());
        RecognizedQuestion question = questions.get(0);
        assertEquals("下级组织认为上级决定不符合本地实际，可（）", question.getStem());
        assertEquals(4, question.getOptions().size());
        assertEquals(List.of("A"), question.getCorrectAnswer());
        assertFalse(question.getStem().contains("未能生成"));
    }
}
