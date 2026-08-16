package com.wangyue.backend.controller;

import com.wangyue.backend.dto.CreateQuestionRequest;
import com.wangyue.backend.dto.PracticeQuestionResponse;
import com.wangyue.backend.dto.QuestionDetailResponse;
import com.wangyue.backend.dto.UpdateQuestionRequest;
import com.wangyue.backend.entity.Question;
import com.wangyue.backend.service.QuestionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public Question create(
        @RequestBody CreateQuestionRequest request,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return questionService.create(request, currentUserId);
    }

    @GetMapping("/library/{libraryId}")
    public List<PracticeQuestionResponse> findByLibraryId(
        @PathVariable Long libraryId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return questionService.findPracticeByLibraryId(libraryId, currentUserId);
    }

    @GetMapping("/{id}")
    public QuestionDetailResponse findDetailById(
        @PathVariable Long id,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return questionService.findDetailById(id, currentUserId);
    }

    @PatchMapping("/{id}")
    public QuestionDetailResponse update(
        @PathVariable Long id,
        @RequestBody UpdateQuestionRequest request,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return questionService.update(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Long currentUserId) {
        questionService.delete(id, currentUserId);
    }
}
