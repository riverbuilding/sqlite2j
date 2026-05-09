package org.sqlite2j.vm;

enum TransactionState {
  IDLE,
  IN_TXN,
  COMMITTING,
  ROLLING_BACK
}
