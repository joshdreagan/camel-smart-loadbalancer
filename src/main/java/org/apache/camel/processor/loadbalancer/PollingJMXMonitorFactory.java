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
import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;

public class PollingJMXMonitorFactory {
  
  private ObjectName objectName;
  private String attributeName;
  private Map<String, ?> environment;

  private Long pollingDelay;
  private Long pollingPeriod;

  public ObjectName getObjectName() {
    return objectName;
  }

  public void setObjectName(ObjectName objectName) {
    this.objectName = objectName;
  }

  public String getAttributeName() {
    return attributeName;
  }

  public void setAttributeName(String attributeName) {
    this.attributeName = attributeName;
  }

  public Map<String, ?> getEnvironment() {
    return environment;
  }

  public void setEnvironment(Map<String, ?> environment) {
    this.environment = environment;
  }

  public long getPollingDelay() {
    return pollingDelay;
  }

  public void setPollingDelay(long pollingDelay) {
    this.pollingDelay = pollingDelay;
  }

  public long getPollingPeriod() {
    return pollingPeriod;
  }

  public void setPollingPeriod(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
  }
  
  public JMXMonitor newJMXMonitor(JMXServiceURL serviceUrl) {
    PollingJMXMonitor monitor = new PollingJMXMonitor(serviceUrl, objectName, attributeName, environment);
    if (pollingDelay != null) {
      monitor.setPollingDelay(pollingDelay);
    }
    if (pollingPeriod != null) {
      monitor.setPollingPeriod(pollingPeriod);
    }
    return monitor;
  }
}
