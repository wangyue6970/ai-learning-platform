package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.dto.CreateQuestionRequest;
import com.wangyue.backend.dto.CreateQuestionOptionRequest;
import com.wangyue.backend.dto.PracticeQuestionResponse;
import com.wangyue.backend.dto.QuestionDetailResponse;
import com.wangyue.backend.dto.QuestionOptionResponse;
import com.wangyue.backend.entity.Question;
import com.wangyue.backend.entity.QuestionOption;
import com.wangyue.backend.mapper.QuestionMapper;
import com.wangyue.backend.mapper.QuestionOptionMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuestionService {

    private final LearningLibraryService learningLibraryService;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final ObjectMapper objectMapper;

    public QuestionService(
        LearningLibraryService learningLibraryService,
        QuestionMapper questionMapper,
        QuestionOptionMapper questionOptionMapper,
        ObjectMapper objectMapper
    ) {
        this.learningLibraryService = learningLibraryService;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.objectMapper = objectMapper;
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("正确答案格式错误", exception);
        }
    }

    private List<String> readAnswers(String jsonText) {
        try {
            return objectMapper.readValue(jsonText, new TypeReference<List<String>>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("题目正确答案格式错误", exception);
        }
    }

    private void validateCreateRequest(CreateQuestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("题目数据不能为空");
        }
        if (request.getLibraryId() == null) {
            throw new IllegalArgumentException("学习库不能为空");
        }
        if (request.getQuestionType() == null || request.getQuestionType().isBlank()) {
            throw new IllegalArgumentException("题型不能为空");
        }
        if (request.getStem() == null || request.getStem().isBlank()) {
            throw new IllegalArgumentException("题干不能为空");
        }
        if (request.getCorrectAnswer() == null || request.getCorrectAnswer().isEmpty()) {
            throw new IllegalArgumentException("正确答案不能为空");
        }
        if (request.getOptions() == null || request.getOptions().isEmpty()) {
            throw new IllegalArgumentException("选项不能为空");
        }
    }

    @Transactional
    public Question create(CreateQuestionRequest request) {
        validateCreateRequest(request);
        if (learningLibraryService.findById(request.getLibraryId()) == null) {
            throw new IllegalArgumentException("学习库不存在");
        }

        Question question = new Question();
        question.setLibraryId(request.getLibraryId());
        question.setQuestionType(request.getQuestionType());
        question.setStem(request.getStem());
        question.setCorrectAnswer(toJson(request.getCorrectAnswer()));
        question.setExplanation(request.getExplanation());
        questionMapper.insert(question);

        for (CreateQuestionOptionRequest optionRequest : request.getOptions()) {
            QuestionOption option = new QuestionOption();
            option.setQuestionId(question.getId());
            option.setOptionKey(optionRequest.getOptionKey());
            option.setContent(optionRequest.getContent());
            option.setSortOrder(optionRequest.getSortOrder());
            questionOptionMapper.insert(option);
        }
        return question;
    }

    public List<Question> findByLibraryId(Long libraryId) {
        if (learningLibraryService.findById(libraryId) == null) {
            throw new IllegalArgumentException("学习库不存在");
        }
        return questionMapper.selectList(
            new LambdaQueryWrapper<Question>()
                .eq(Question::getLibraryId, libraryId)
                .orderByAsc(Question::getId)
        );
    }

    public QuestionDetailResponse findDetailById(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new IllegalArgumentException("题目不存在");
        }

        List<QuestionOptionResponse> optionResponses = questionOptionMapper.selectList(
            new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, question.getId())
                .orderByAsc(QuestionOption::getSortOrder)
        ).stream().map(option -> {
            QuestionOptionResponse response = new QuestionOptionResponse();
            response.setOptionKey(option.getOptionKey());
            response.setContent(option.getContent());
            response.setSortOrder(option.getSortOrder());
            return response;
        }).toList();

        QuestionDetailResponse response = new QuestionDetailResponse();
        response.setId(question.getId());
        response.setLibraryId(question.getLibraryId());
        response.setQuestionType(question.getQuestionType());
        response.setStem(question.getStem());
        response.setOptions(optionResponses);
        response.setCorrectAnswer(readAnswers(question.getCorrectAnswer()));
        response.setExplanation(question.getExplanation());
        return response;
    }

    public List<PracticeQuestionResponse> findPracticeByLibraryId(Long libraryId) {
        List<PracticeQuestionResponse> responses = new ArrayList<>();

        for (Question question : findByLibraryId(libraryId)) {
            PracticeQuestionResponse response = new PracticeQuestionResponse();
            response.setId(question.getId());
            response.setLibraryId(question.getLibraryId());
            response.setQuestionType(question.getQuestionType());
            response.setStem(question.getStem());

            List<QuestionOptionResponse> optionResponses = new ArrayList<>();
            List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                    .eq(QuestionOption::getQuestionId, question.getId())
                    .orderByAsc(QuestionOption::getSortOrder)
            );
            for (QuestionOption option : options) {
                QuestionOptionResponse optionResponse = new QuestionOptionResponse();
                optionResponse.setOptionKey(option.getOptionKey());
                optionResponse.setContent(option.getContent());
                optionResponse.setSortOrder(option.getSortOrder());
                optionResponses.add(optionResponse);
            }

            response.setOptions(optionResponses);
            responses.add(response);
        }
        return responses;
    }
}
