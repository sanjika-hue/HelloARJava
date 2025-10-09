package com.google.ar.core.examples.helloar;

import com.google.ar.core.examples.helloar.common.samplerender.Mesh;

class SafeMesh {
    Mesh mesh;
    boolean valid = true;

    SafeMesh(Mesh mesh) {
        this.mesh = mesh;
    }

    void invalidate() {
        valid = false; // mark it as no longer drawable
    }

    boolean isValid() {
        return valid && mesh != null;
    }
}