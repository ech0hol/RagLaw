package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DocumentTitleLockService {

    private final JdbcTemplate jdbcTemplate;

    public DocumentTitleLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public <T> T withTitleLock(String title, Supplier<T> action) {
        String lockName = "raglaw:doc:title:" + title;
        try {
            Integer acquired = jdbcTemplate.queryForObject(
                    "SELECT GET_LOCK(?, ?)",
                    Integer.class,
                    lockName,
                    30
            );
            if (acquired == null || acquired != 1) {
                throw new BusinessException(ErrorCodes.VALIDATION, "同名文档正在处理，请稍后重试");
            }
            try {
                return action.get();
            } finally {
                jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
            }
        } catch (DataAccessException ex) {
            if (isUnsupportedTitleLock(ex)) {
                return action.get();
            }
            throw ex;
        }
    }

    private static boolean isUnsupportedTitleLock(DataAccessException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains("GET_LOCK");
    }
}
