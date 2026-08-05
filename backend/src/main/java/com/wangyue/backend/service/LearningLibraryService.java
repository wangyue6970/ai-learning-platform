package com.wangyue.backend.service;

import com.wangyue.backend.entity.LearningLibrary;
import com.wangyue.backend.mapper.LearningLibraryMapper;
import java.util.List;
import org.springframework.stereotype.Service;

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

    public LearningLibrary create(String name) {
        validateName(name);
        LearningLibrary library = new LearningLibrary();
        library.setName(name);
        learningLibraryMapper.insert(library);
        return library;
    }

    public List<LearningLibrary> findAll() {
        return learningLibraryMapper.selectList(null);
    }

    public LearningLibrary findById(Long id) {
        return learningLibraryMapper.selectById(id);
    }

    public LearningLibrary update(Long id, String name) {
        validateName(name);
        LearningLibrary library = findById(id);
        if (library == null) {
            throw new IllegalArgumentException("学习库不存在");
        }
        library.setName(name);
        learningLibraryMapper.updateById(library);
        return library;
    }

    public void delete(Long id) {
        LearningLibrary library = findById(id);
        if (library == null) {
            throw new IllegalArgumentException("学习库不存在");
        }
        learningLibraryMapper.deleteById(id);
    }
}
