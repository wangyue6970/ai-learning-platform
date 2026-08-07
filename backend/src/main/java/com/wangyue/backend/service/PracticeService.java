package com.wangyue.backend.service;

import com.wangyue.backend.dto.SubmitAnswerRequest;
import com.wangyue.backend.dto.SubmitAnswerResponse;
import com.wangyue.backend.dto.PracticeQuestionResponse;
import com.wangyue.backend.entity.AnswerRecord;
import com.wangyue.backend.entity.Question;
import com.wangyue.backend.entity.WrongQuestion;
import com.wangyue.backend.mapper.AnswerRecordMapper;
import com.wangyue.backend.mapper.QuestionMapper;
import com.wangyue.backend.mapper.WrongQuestionMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PracticeService {

    private final QuestionMapper questionMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;

    public PracticeService(
        QuestionMapper questionMapper,
        AnswerRecordMapper answerRecordMapper,
        WrongQuestionMapper wrongQuestionMapper,
        QuestionService questionService,
        ObjectMapper objectMapper
    ) {
        this.questionMapper = questionMapper;
        this.answerRecordMapper = answerRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionService = questionService;
        this.objectMapper = objectMapper;
    }

    private List<String> readAnswers(String jsonText) {
        try {
            return objectMapper.readValue(
                jsonText,
                new TypeReference<List<String>>() {}
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("题目正确答案格式错误", exception);
        }
    }

    private boolean answersMatch(List<String> selectedAnswers, List<String> correctAnswers) {
        Set<String> selectedSet = new HashSet<>(selectedAnswers);
        Set<String> correctSet = new HashSet<>(correctAnswers);

        return selectedAnswers.size() == selectedSet.size()
            && correctAnswers.size() == correctSet.size()
            && selectedSet.equals(correctSet);
    }

    private String writeAnswers(List<String> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("用户答案格式错误", exception);
        }
    }

    private Question validateAndFindQuestion(SubmitAnswerRequest request) {
        if (request == null || request.getLibraryId() == null || request.getQuestionId() == null
            || request.getSelectedAnswer() == null || request.getSelectedAnswer().isEmpty()) {
            throw new IllegalArgumentException("学习库、题目和作答内容不能为空");
        }

        Question question = questionMapper.selectById(request.getQuestionId());
        if (question == null || !question.getLibraryId().equals(request.getLibraryId())) {
            throw new IllegalArgumentException("题目不存在或不属于该学习库");
        }
        return question;
    }

    private void saveAnswerRecord(SubmitAnswerRequest request, boolean correct) {
        AnswerRecord record = new AnswerRecord();
        record.setLibraryId(request.getLibraryId());
        record.setQuestionId(request.getQuestionId());
        record.setSelectedAnswer(writeAnswers(request.getSelectedAnswer()));
        record.setCorrect(correct);
        answerRecordMapper.insert(record);
    }

    private WrongQuestion findWrongQuestion(Long libraryId, Long questionId) {
        return wrongQuestionMapper.selectOne(new LambdaQueryWrapper<WrongQuestion>()
            .eq(WrongQuestion::getLibraryId, libraryId)
            .eq(WrongQuestion::getQuestionId, questionId));
    }

    private void updateWrongQuestionState(
        SubmitAnswerRequest request, boolean correct, SubmitAnswerResponse response
    ) {
        WrongQuestion wrongQuestion = findWrongQuestion(request.getLibraryId(), request.getQuestionId());

        if (!correct) {
            if (wrongQuestion == null) {
                wrongQuestion = new WrongQuestion();
                wrongQuestion.setLibraryId(request.getLibraryId());
                wrongQuestion.setQuestionId(request.getQuestionId());
                wrongQuestionMapper.insert(wrongQuestion);
            } else {
                wrongQuestion.setConsecutiveCorrectCount(0);
                wrongQuestionMapper.updateById(wrongQuestion);
            }
            response.setConsecutiveCorrectCount(0);
            response.setRemovedFromWrongQuestions(false);
            return;
        }

        if (wrongQuestion == null) {
            response.setConsecutiveCorrectCount(0);
            response.setRemovedFromWrongQuestions(false);
            return;
        }

        int nextCount = wrongQuestion.getConsecutiveCorrectCount() + 1;
        if (nextCount >= 2) {
            wrongQuestionMapper.deleteById(wrongQuestion.getId());
            response.setRemovedFromWrongQuestions(true);
        } else {
            wrongQuestion.setConsecutiveCorrectCount(nextCount);
            wrongQuestionMapper.updateById(wrongQuestion);
            response.setRemovedFromWrongQuestions(false);
        }
        response.setConsecutiveCorrectCount(nextCount);
    }

    @Transactional
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        Question question = validateAndFindQuestion(request);
        List<String> correctAnswers = readAnswers(question.getCorrectAnswer());
        boolean correct = answersMatch(request.getSelectedAnswer(), correctAnswers);

        saveAnswerRecord(request, correct);

        SubmitAnswerResponse response = new SubmitAnswerResponse();
        response.setCorrect(correct);
        response.setCorrectAnswer(correctAnswers);
        response.setExplanation(question.getExplanation());
        updateWrongQuestionState(request, correct, response);
        return response;
    }

    public List<PracticeQuestionResponse> findWrongQuestionsByLibraryId(Long libraryId) {
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(
            new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getLibraryId, libraryId)
        );
        Set<Long> wrongQuestionIds = new HashSet<>();
        for (WrongQuestion wrongQuestion : wrongQuestions) {
            wrongQuestionIds.add(wrongQuestion.getQuestionId());
        }
        return questionService.findPracticeByLibraryId(libraryId).stream()
            .filter(question -> wrongQuestionIds.contains(question.getId()))
            .toList();
    }
}
