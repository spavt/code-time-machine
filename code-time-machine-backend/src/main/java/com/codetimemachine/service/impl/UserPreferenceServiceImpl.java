package com.codetimemachine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codetimemachine.entity.UserPreference;
import com.codetimemachine.mapper.UserPreferenceMapper;
import com.codetimemachine.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private final UserPreferenceMapper userPreferenceMapper;

    @Override
    public UserPreference getBySessionId(String sessionId) {
        return userPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getSessionId, sessionId));
    }

    @Override
    public void saveSkillLevel(String sessionId, String skillLevel) {
        UserPreference existing = getBySessionId(sessionId);

        if (existing != null) {
            existing.setSkillLevel(skillLevel);
            userPreferenceMapper.updateById(existing);
        } else {
            UserPreference preference = new UserPreference();
            preference.setSessionId(sessionId);
            preference.setSkillLevel(skillLevel);
            userPreferenceMapper.insert(preference);
        }
    }
}
