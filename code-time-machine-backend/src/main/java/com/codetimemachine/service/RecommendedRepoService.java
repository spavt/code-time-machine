package com.codetimemachine.service;

import com.codetimemachine.dto.RecommendedRepoDTO;

import java.util.List;

public interface RecommendedRepoService {

    List<RecommendedRepoDTO> getByLevel(String level);

    List<RecommendedRepoDTO> getAll();
}
