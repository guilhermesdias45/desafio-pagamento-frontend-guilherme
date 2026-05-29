package com.acaboumony.payment.mapper;

import com.acaboumony.payment.domain.entity.Transaction;
import com.acaboumony.payment.dto.response.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "refunds", ignore = true)
    TransactionResponse toResponse(Transaction transaction);
}
