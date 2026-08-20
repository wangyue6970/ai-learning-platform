package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.entity.LearningLibrary;
import com.wangyue.backend.mapper.LearningLibraryMapper;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningLibraryService {

    private final LearningLibraryMapper learningLibraryMapper;

    public LearningLibraryService(LearningLibraryMapper learningLibraryMapper) {
        this.learningLibraryMapper = learningLibraryMapper;
    }

    public void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("学习库名称不能为空");
        }
    }

    public LearningLibrary create(String name, Long currentUserId) {
        validateName(name);
        LearningLibrary library = new LearningLibrary();
        library.setName(name);
        library.setOwnerId(requireCurrentUserId(currentUserId));
        learningLibraryMapper.insert(library);
        return library;
    }

    public List<LearningLibrary> findAllOwnedBy(Long currentUserId) {
        return learningLibraryMapper.selectList(
            new LambdaQueryWrapper<LearningLibrary>()
                .eq(LearningLibrary::getOwnerId, requireCurrentUserId(currentUserId))
                .orderByAsc(LearningLibrary::getId)
        );
    }

    /**
     * Internal existence lookup. Public HTTP endpoints must call
     * findOwnedById so that an authenticated user cannot read another owner's
     * library by guessing its id.
     */
    public LearningLibrary findById(Long id) {
        return learningLibraryMapper.selectById(id);
    }

    public LearningLibrary findOwnedById(Long id, Long currentUserId) {
        LearningLibrary library = learningLibraryMapper.selectOne(
            new LambdaQueryWrapper<LearningLibrary>()
                .eq(LearningLibrary::getId, id)
                .eq(LearningLibrary::getOwnerId, requireCurrentUserId(currentUserId))
        );
        if (library == null) {
            throw new AccessDeniedException("无权访问该学习库");
        }
        return library;
    }

    public LearningLibrary update(Long id, String name, Long currentUserId) {
        validateName(name);
        LearningLibrary library = findOwnedById(id, currentUserId);
        library.setName(name);
        learningLibraryMapper.updateById(library);
        return library;
    }

    @Transactional
    public void delete(Long id, Long currentUserId) {
        LearningLibrary library = findOwnedById(id, currentUserId);
        int deletedCount = learningLibraryMapper.deleteById(library.getId());
        if (deletedCount != 1) {
            throw new IllegalStateException("学习库删除失败，请刷新后重试");
        }
    }

    private Long requireCurrentUserId(Long currentUserId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new AccessDeniedException("无权访问该学习库");
        }
        return currentUserId;
    }
}
