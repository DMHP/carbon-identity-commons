/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.identity.common.base.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.common.base.event.model.Event;
import org.wso2.carbon.identity.common.base.exception.IdentityException;
import org.wso2.carbon.identity.common.base.handler.IdentityEventHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Command stack for event handling.
 */
public class CommandStack {

    private static final Logger log = LoggerFactory.getLogger(CommandStack.class);
    private Deque<Command> commandsStack = new ArrayDeque<>();

    public void execute(IdentityEventHandler handler, EventContext eventContext, Event event) throws IdentityException {

        Command command = new Command(handler, eventContext, event);
        commandsStack.add(command);

        try {
            handler.handle(eventContext, event);
        } catch (IdentityException e) {
            throw this.rollback(e, handler, event);
        }
    }

    private IdentityException rollback(IdentityException original, IdentityEventHandler identityEventHandler, Event
            event) {

        List<IdentityException> exceptionList = new ArrayList<>();
        StringBuilder builder = new StringBuilder("Error occurred in handler: " + identityEventHandler.getName() +
                                                  "for event: " + event.getEventName()).append("\n");

        while (!commandsStack.isEmpty()) {
            Command command = commandsStack.pop();
            try {
                command.rollback();
            } catch (IdentityException e) {
                //Suppress all the exceptions of rollback failures to a single exception.
                exceptionList.add(e);
                String failure = String.format("Error during rollback operation of handler: %s for event: %s",
                                               command.getIdentityEventHandler().getName(),
                                               command.getEvent().getEventName());
                log.error("Error during rollback operation of " + failure, e);
                builder.append(failure).append("\n");
            }
        }

        IdentityException ex = new IdentityException(builder.toString(), original);

        //Java 8 forEach is not used to avoid a findbugs false positive: UC_USELESS_OBJECT.
        for (IdentityException exp: exceptionList) {
            ex.addSuppressed(exp);
        }
        return ex;
    }
}
