/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.loadbalancer;

import java.util.Map;
import javax.management.AttributeChangeNotification;
import javax.management.AttributeChangeNotificationFilter;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;

public class NotifiedJMXMonitor extends JMXMonitor {

  public NotifiedJMXMonitor(JMXServiceURL serviceUrl, ObjectName objectName, String attributeName) {
    super(serviceUrl, objectName, attributeName);
  }

  public NotifiedJMXMonitor(JMXServiceURL serviceUrl, ObjectName objectName, String attributeName, Map<String, ?> environment) {
    super(serviceUrl, objectName, attributeName, environment);
  }

  @Override
  protected void initJmxConnector() throws Exception {
    super.initJmxConnector();

    jmxConnector.addConnectionNotificationListener(new NotificationListener() {
      @Override
      public void handleNotification(Notification notification, Object handback) {
        try {
          AttributeChangeNotificationFilter filter = new AttributeChangeNotificationFilter();
          filter.enableAttribute(getAttributeName());
          jmxConnector.getMBeanServerConnection().addNotificationListener(getObjectName(), new NotificationListener() {
            @Override
            public void handleNotification(Notification notification, Object handback) {
              Object attributeValue = ((AttributeChangeNotification) notification).getNewValue();
              setValue(attributeValue);
            }
          }, filter, null);
        } catch (Exception e) {
          log.error(String.format("Unable to create attribute value listener for [%s[%s/%s]].", getServiceUrl(), getObjectName(), getAttributeName()), (log.isDebugEnabled()) ? e : null);
          throw new RuntimeException(e);
        }
      }
    }, new NotificationFilter() {
      @Override
      public boolean isNotificationEnabled(Notification notification) {
        return "jmx.remote.connection.opened".equalsIgnoreCase(notification.getType());
      }
    }, null);
  }
}
