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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Service;

public class MonitoringLoadBalancer extends QueueLoadBalancer {

  private List<ObservableMonitor> monitors = new ArrayList<>();
  private Comparator<Object> valueComparator = new DefaultComparator<>();

  private final Map<Processor, ObservableMonitor> processorToMonitorMap = new HashMap<>();

  private ScheduledExecutorService scheduler;
  private long schedulerDelay = 0L;
  private long schedulerPeriod = 5000L;

  private final AtomicBoolean needSort = new AtomicBoolean(false);

  public List<ObservableMonitor> getMonitors() {
    return monitors;
  }

  public void setMonitors(List<ObservableMonitor> monitors) {
    this.monitors = Objects.requireNonNull(monitors, "The monitors parameter must not be null.");
  }

  public long getSchedulerDelay() {
    return schedulerDelay;
  }

  public void setSchedulerDelay(long schedulerDelay) {
    if (schedulerDelay < 0) {
      throw new IllegalArgumentException("The schedulerDelay parameter must be >= 0.");
    }
    this.schedulerDelay = schedulerDelay;
  }

  public long getSchedulerPeriod() {
    return schedulerPeriod;
  }

  public void setSchedulerPeriod(long schedulerPeriod) {
    if (schedulerPeriod <= 0) {
      throw new IllegalArgumentException("The schedulerPeriod parameter must be > 0.");
    }
    this.schedulerPeriod = schedulerPeriod;
  }

  public Comparator<Object> getValueComparator() {
    return valueComparator;
  }

  public void setValueComparator(Comparator<Object> valueComparator) {
    this.valueComparator = Objects.requireNonNull(valueComparator, "The valueComparator parameter must not be null.");
  }

  @Override
  protected void doStart() throws Exception {
    int processorsSize = (getProcessors() == null) ? 0 : getProcessors().size();
    int monitorsSize = (monitors == null) ? 0 : monitors.size();
    if (processorsSize == monitorsSize) {
      for (int i = 0; i < processorsSize; ++i) {
        monitors.get(i).addObserver(new Observer() {
          @Override
          public void update(Observable o, Object arg) {
            needSort.set(true);
          }
        });
        processorToMonitorMap.put(getProcessors().get(i), monitors.get(i));
        if (monitors.get(i) instanceof Service) {
          ((Service) monitors.get(i)).start();
        }
      }
    } else {
      throw new IllegalArgumentException(String.format("Processor list size [%s] must match monitor list size [%s].", processorsSize, monitorsSize));
    }

    if (scheduler == null || scheduler.isShutdown()) {
      scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    scheduler.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        log.debug("Checking if sorting is needed...");
        if (needSort.get()) {
          try {
            log.debug("Sorting processor list.");
            getProcessors().sort(new Comparator<Processor>() {
              @Override
              public int compare(Processor o1, Processor o2) {
                return valueComparator.compare(processorToMonitorMap.get(o1).getValue(), processorToMonitorMap.get(o2).getValue());
              }
            });
          } catch (Exception e) {
            log.warn("Failed to sort processor list.", (log.isDebugEnabled()) ? e : null);
          }
        }
      }
    }, schedulerDelay, schedulerPeriod, TimeUnit.MILLISECONDS);

    super.doStart();
  }

  @Override
  protected void doStop() throws Exception {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdownNow();
    }

    if (monitors != null) {
      for (int i = 0; i < monitors.size(); ++i) {
        try {
          if (monitors.get(i) instanceof Service) {
            ((Service) monitors.get(i)).stop();
          }
        } catch (Exception e) {
          log.warn(String.format("Unable to stop monitor [%s].", i), (log.isDebugEnabled()) ? e : null);
        }
      }
    }

    super.doStop();
  }

  @Override
  protected Processor chooseProcessor(List<Processor> processors, Exchange exchange) {
    return (processors != null && processors.size() > 0) ? processors.get(0) : null;
  }
}
