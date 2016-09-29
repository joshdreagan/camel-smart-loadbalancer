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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;

public class PollingJMXMonitor extends JMXMonitor {

  private long pollingDelay = 5000L;
  private long pollingPeriod = 5000L;

  private ScheduledExecutorService scheduler;
  private Future<?> attributeMonitorHandle;

  public PollingJMXMonitor(JMXServiceURL serviceUrl, ObjectName objectName, String attributeName) {
    super(serviceUrl, objectName, attributeName);
  }

  public PollingJMXMonitor(JMXServiceURL serviceUrl, ObjectName objectName, String attributeName, Map<String, ?> environment) {
    super(serviceUrl, objectName, attributeName, environment);
  }

  public long getPollingDelay() {
    return pollingDelay;
  }

  public void setPollingDelay(long pollingDelay) {
    if (pollingDelay < 0) {
      throw new IllegalArgumentException("The pollingDelay parameter must be >= 0.");
    }
    this.pollingDelay = pollingDelay;
  }

  public long getPollingPeriod() {
    return pollingPeriod;
  }

  public void setPollingPeriod(long pollingPeriod) {
    if (pollingPeriod <= 0) {
      throw new IllegalArgumentException("The pollingPeriod parameter must be > 0.");
    }
    this.pollingPeriod = pollingPeriod;
  }

  protected void tryUpdateAttributeValue() {
    try {
      Object attributeValue = jmxConnector.getMBeanServerConnection().getAttribute(getObjectName(), getAttributeName());
      setValue(attributeValue);
    } catch (Exception e) {
      log.warn(String.format("Unable to get attribute value for [%s[%s/%s]].", getServiceUrl(), getObjectName(), getAttributeName()), (log.isDebugEnabled()) ? e : null);
    }
  }

  protected void startMonitoringAttributeValue() {
    if (attributeMonitorHandle == null || attributeMonitorHandle.isDone()) {
      attributeMonitorHandle = scheduler.scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          tryUpdateAttributeValue();
        }
      }, pollingDelay, pollingPeriod, TimeUnit.MILLISECONDS);
    }
  }

  protected void stopMonitoringAttributeValue() {
    if (attributeMonitorHandle != null) {
      attributeMonitorHandle.cancel(false);
    }
  }

  @Override
  protected void initJmxConnector() throws Exception {
    super.initJmxConnector();

    jmxConnector.addConnectionNotificationListener(new NotificationListener() {
      @Override
      public void handleNotification(Notification notification, Object handback) {
        startMonitoringAttributeValue();
      }
    }, new NotificationFilter() {
      @Override
      public boolean isNotificationEnabled(Notification notification) {
        return "jmx.remote.connection.opened".equalsIgnoreCase(notification.getType());
      }
    }, null);
  }

  @Override
  public void start() throws Exception {
    if (scheduler == null || scheduler.isShutdown()) {
      scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    super.start();
  }

  @Override
  public void stop() throws Exception {
    if (attributeMonitorHandle != null) {
      attributeMonitorHandle.cancel(true);
    }
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
    }

    super.stop();
  }
}
