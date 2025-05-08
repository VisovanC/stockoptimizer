package com.cv.stockoptimizer.repository;

import com.cv.stockoptimizer.model.entity.MLModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface MLModelRepository extends MongoRepository<MLModel, String> {

    Optional<MLModel> findBySymbol(String symbol);

    List<MLModel> findByModelType(String modelType);
}