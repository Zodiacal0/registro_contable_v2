package com.registrogastos.ui;

/**
 * Una vista que sabe hacer su entrada (esqueleto + cascada). La ventana la llama al cambiar
 * de vista sin necesidad de saber cómo está armada cada una.
 */
interface Revelable {

    void revelar();
}
