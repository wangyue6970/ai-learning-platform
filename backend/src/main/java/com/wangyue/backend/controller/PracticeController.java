package com.wangyue.backend.controller;

import com.wangyue.backend.dto.SubmitAnswerRequest;
import com.wangyue.backend.dto.SubmitAnswerResponse;
import com.wangyue.backend.dto.PracticeQuestionResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.wangyue.backend.service.PracticeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @PostMapping("/answers")
    public SubmitAnswerResponse submitAnswer(
        @RequestBody SubmitAnswerRequest request,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return practiceService.submitAnswer(request, currentUserId);
    }

    @GetMapping("/wrong-questions/library/{libraryId}")
    public List<PracticeQuestionResponse> findWrongQuestions(
        @PathVariable Long libraryId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return practiceService.findWrongQuestionsByLibraryId(libraryId, currentUserId);
    }
}
