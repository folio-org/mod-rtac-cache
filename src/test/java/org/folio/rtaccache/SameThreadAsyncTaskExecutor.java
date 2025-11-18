package org.folio.rtaccache;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/**
 * Test AsyncTaskExecutor that executes every task synchronously
 * in the calling thread.
 */
public class SameThreadAsyncTaskExecutor implements AsyncTaskExecutor {

  @Override
  public void execute(Runnable task) throws TaskRejectedException {
    task.run();
  }

  @Override
  public void execute(Runnable task, long startTimeout) throws TaskRejectedException {
    task.run();
  }

}
