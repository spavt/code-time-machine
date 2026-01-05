package com.codetimemachine.service;

import com.codetimemachine.entity.UserPreference;

public interface UserPreferenceService {

    UserPreference getBySessionId(String sessionId);

    void saveSkillLevel(String sessionId, String skillLevel);
}
