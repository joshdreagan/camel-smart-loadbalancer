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
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.apache.camel.Service;

public class JMXMonitor extends ObservableMonitor implements Service {

  private final JMXServiceURL serviceUrl;
  private final ObjectName objectName;
  private final String attributeName;
  private final Map<String, ?> environment;
  
  private long retryPeriod = 5000L;

  protected JMXConnector jmxConnector;
  private final AtomicBoolean connected = new AtomicBoolean(false);

  private ScheduledExecutorService scheduler;
  private Future<?> openHandle;
  private Future<?> closeHandle;

  public JMXMonitor(JMXServiceURL serviceUrl, ObjectName objectName, String attributeName) {
    this(serviceUrl, objectName, attributeName, null);
  }

  public JMXMonitor(JMXServiceURL serviceUrl, ObjectName objectName, String attributeName, Map<String, ?> environment) {
    this.serviceUrl = Objects.requireNonNull(serviceUrl, "The serviceUrl parameter must not be null.");
    this.objectName = Objects.requireNonNull(objectName, "The objectName parameter must not be null.");
    this.attributeName = Objects.requireNonNull(attributeName, "The attributeName parameter must not be null.");
    this.environment = environment;
  }

  public JMXServiceURL getServiceUrl() {
    return serviceUrl;
  }

  public ObjectName getObjectName() {
    return objectName;
  }

  public String getAttributeName() {
    return attributeName;
  }

  public Map<String, ?> getEnvironment() {
    return environment;
  }

  public long getRetryPeriod() {
    return retryPeriod;
  }

  public void setRetryPeriod(long retryPeriod) {
    if (retryPeriod <= 0) {
      throw new IllegalArgumentException("The retryPeriod parameter must be > 0.");
    }
    this.retryPeriod = retryPeriod;
  }

  public boolean isConnected() {
    return connected.get();
  }

  protected void initJmxConnector() throws Exception {
    try {
      jmxConnector = JMXConnectorFactory.newJMXConnector(serviceUrl, environment);

      jmxConnector.addConnectionNotificationListener(new NotificationListener() {
        @Override
        public void handleNotification(Notification notification, Object handback) {
          jmxConnector = null;
          connected.set(false);
          if (closeHandle != null) {
            closeHandle.cancel(false);
          }
        }
      }, new NotificationFilter() {
        @Override
        public boolean isNotificationEnabled(Notification notification) {
          return "jmx.remote.connection.closed".equalsIgnoreCase(notification.getType());
        }
      }, null);

      jmxConnector.addConnectionNotificationListener(new NotificationListener() {
        @Override
        public void handleNotification(Notification notification, Object handback) {
          jmxConnector = null;
          connected.set(false);
          if (openHandle == null || openHandle.isDone()) {
            openJmxConnector();
          }
        }
      }, new NotificationFilter() {
        @Override
        public boolean isNotificationEnabled(Notification notification) {
          return "jmx.remote.connection.failed".equalsIgnoreCase(notification.getType());
        }
      }, null);

      jmxConnector.addConnectionNotificationListener(new NotificationListener() {
        @Override
        public void handleNotification(Notification notification, Object handback) {
          connected.set(true);
          if (openHandle != null) {
            openHandle.cancel(false);
          }
        }
      }, new NotificationFilter() {
        @Override
        public boolean isNotificationEnabled(Notification notification) {
          return "jmx.remote.connection.opened".equalsIgnoreCase(notification.getType());
        }
      }, null);
    } catch (Exception e) {
      log.warn(String.format("Unable to initialize JMX connection for [%s].", serviceUrl), (log.isDebugEnabled()) ? e : null);
      throw e;
    }
  }

  protected void tryCloseJmxConnector() {
    if (jmxConnector != null) {
      try {
        jmxConnector.close();
      } catch (Exception e) {
        log.warn(String.format("Unable to close JMX connection for [%s].", serviceUrl), (log.isDebugEnabled()) ? e : null);
      }
      jmxConnector = null;
    }
  }

  protected void closeJmxConnector() {
    closeHandle = scheduler.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        if (connected.get()) {
          tryCloseJmxConnector();
        }
      }
    }, 0L, 5000L, null);
  }
  
  protected boolean tryOpenJmxConnector() {
    boolean opened = false;
    try {
      if (jmxConnector == null) {
        initJmxConnector();
      }

      jmxConnector.connect();
      opened = true;
    } catch (Exception e) {
      log.warn(String.format("Unable to open JMX connection for [%s].", serviceUrl), (log.isDebugEnabled()) ? e : null);
    }
    return opened;
  }
  
  protected void openJmxConnector() {
    openHandle = scheduler.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        if (!connected.get()) {
          tryOpenJmxConnector();
        }
      }
    }, retryPeriod, retryPeriod, TimeUnit.MILLISECONDS);
  }

  @Override
  public void start() throws Exception {
    if (scheduler == null || scheduler.isShutdown()) {
      scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    
    openJmxConnector();
  }

  @Override
  public void stop() throws Exception {
    closeJmxConnector();

    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
    }
  }
}
