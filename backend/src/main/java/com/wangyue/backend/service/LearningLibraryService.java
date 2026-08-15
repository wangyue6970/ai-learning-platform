package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.entity.AppUser;
import com.wangyue.backend.entity.LearningLibrary;
import com.wangyue.backend.mapper.AppUserMapper;
import com.wangyue.backend.mapper.LearningLibraryMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LearningLibraryService {

    private static final String LOCAL_DEMO_USERNAME = "demo-user";

    private final AppUserMapper appUserMapper;
    private final LearningLibraryMapper learningLibraryMapper;

    public LearningLibraryService(
        AppUserMapper appUserMapper,
        LearningLibraryMapper learningLibraryMapper
    ) {
        this.appUserMapper = appUserMapper;
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
        library.setOwnerId(findLocalDemoUserId());
        learningLibraryMapper.insert(library);
        return library;
    }

    private Long findLocalDemoUserId() {
        AppUser demoUser = appUserMapper.selectOne(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, LOCAL_DEMO_USERNAME)
        );

        if (demoUser == null) {
            throw new IllegalStateException("本地演示账号不存在，无法创建学习库");
        }

        return demoUser.getId();
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
