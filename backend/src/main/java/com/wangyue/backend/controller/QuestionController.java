package com.wangyue.backend.controller;

import com.wangyue.backend.dto.CreateQuestionRequest;
import com.wangyue.backend.dto.PracticeQuestionResponse;
import com.wangyue.backend.dto.QuestionDetailResponse;
import com.wangyue.backend.dto.UpdateQuestionRequest;
import com.wangyue.backend.entity.Question;
import com.wangyue.backend.service.QuestionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public Question create(@RequestBody CreateQuestionRequest request) {
        return questionService.create(request);
    }

    @GetMapping("/library/{libraryId}")
    public List<PracticeQuestionResponse> findByLibraryId(@PathVariable Long libraryId) {
        return questionService.findPracticeByLibraryId(libraryId);
    }

    @GetMapping("/{id}")
    public QuestionDetailResponse findDetailById(@PathVariable Long id) {
        return questionService.findDetailById(id);
    }

    @PatchMapping("/{id}")
    public QuestionDetailResponse update(
        @PathVariable Long id,
        @RequestBody UpdateQuestionRequest request
    ) {
        return questionService.update(id, request);
    }
}
