package com.codetimemachine.service.impl;

import com.codetimemachine.dto.RecommendedRepoDTO;
import com.codetimemachine.service.RecommendedRepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendedRepoServiceImpl implements RecommendedRepoService {

    private final Map<String, List<RecommendedRepoDTO>> recommendedReposByLevel;

    @Override
    public List<RecommendedRepoDTO> getByLevel(String level) {
        return recommendedReposByLevel.getOrDefault(level, Collections.emptyList());
    }

    @Override
    public List<RecommendedRepoDTO> getAll() {
        List<RecommendedRepoDTO> all = new ArrayList<>();
        recommendedReposByLevel.values().forEach(all::addAll);
        return all;
    }
}
