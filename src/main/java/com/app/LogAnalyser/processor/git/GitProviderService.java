package com.app.LogAnalyser.processor.git;

import com.app.LogAnalyser.model.GitAnalysisResult;


public interface GitProviderService {
    GitAnalysisResult analyse(String stackTrace);
}