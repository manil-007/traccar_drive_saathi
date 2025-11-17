/*
 * Copyright 2025 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.model.ValidationLog;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import java.util.Date;

public class ValidationLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationLogService.class);

    private final Storage storage;

    @Inject
    public ValidationLogService(Storage storage) {
        this.storage = storage;
    }

    /**
     * Log a successful validation entry
     * 
     * @param userId         The user ID
     * @param validationType "license" or "vehicle"
     * @param number         The DL number or vehicle registration number
     * @param status         The validation status (e.g., "pass")
     */
    public void logValidation(long userId, String validationType, String number, String status) {
        try {
            ValidationLog log = new ValidationLog();
            log.setUserId(userId);
            log.setValidationType(validationType);
            log.setNumber(number);
            log.setValidationDate(new Date());
            log.setStatus(status);

            storage.addObject(log, new Request(new Columns.Exclude("id")));
            LOGGER.info("Logged validation: type={}, number={}, user={}", validationType, number, userId);
        } catch (StorageException e) {
            LOGGER.error("Failed to log validation for user {} type {} number {}", userId, validationType, number, e);
        }
    }
}
