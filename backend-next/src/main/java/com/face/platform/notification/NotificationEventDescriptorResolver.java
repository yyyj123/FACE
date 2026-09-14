package com.face.platform.notification;

import java.util.List;
import java.util.Set;

public interface NotificationEventDescriptorResolver {

    Set<String> eventTypes();

    List<NotificationMessageDraft> resolve(NotificationEvent event);
}
