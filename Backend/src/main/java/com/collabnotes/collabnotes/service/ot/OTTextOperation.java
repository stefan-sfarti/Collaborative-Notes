package com.collabnotes.collabnotes.service.ot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable text OT operation defined as an ordered component list.
 */
public final class OTTextOperation {
    private final String operationId;
    private final String clientId;
    private final long baseRevision;
    private final List<OTComponent> components;

    private OTTextOperation(String operationId, String clientId, long baseRevision, List<OTComponent> components) {
        this.operationId = operationId;
        this.clientId = clientId;
        this.baseRevision = baseRevision;
        this.components = List.copyOf(components);
    }

    public String operationId() {
        return operationId;
    }

    public String clientId() {
        return clientId;
    }

    public long baseRevision() {
        return baseRevision;
    }

    public List<OTComponent> components() {
        return components;
    }

    public int baseLength() {
        int len = 0;
        for (OTComponent c : components) {
            if (c instanceof OTRetain || c instanceof OTDelete) {
                len += c.length();
            }
        }
        return len;
    }

    public int targetLength() {
        int len = 0;
        for (OTComponent c : components) {
            if (c instanceof OTRetain || c instanceof OTInsert) {
                len += c.length();
            }
        }
        return len;
    }

    public static Builder builder(String operationId, String clientId, long baseRevision) {
        return new Builder(operationId, clientId, baseRevision);
    }

    public static final class Builder {
        private final String operationId;
        private final String clientId;
        private final long baseRevision;
        private final List<OTComponent> components = new ArrayList<>();

        private Builder(String operationId, String clientId, long baseRevision) {
            this.operationId = operationId;
            this.clientId = clientId;
            this.baseRevision = baseRevision;
        }

        public Builder retain(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("Retain count must be > 0");
            }
            append(new OTRetain(count));
            return this;
        }

        public Builder insert(String text) {
            if (text == null || text.isEmpty()) {
                throw new IllegalArgumentException("Insert text must be non-empty");
            }
            append(new OTInsert(text));
            return this;
        }

        public Builder delete(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("Delete count must be > 0");
            }
            append(new OTDelete(count));
            return this;
        }

        private void append(OTComponent next) {
            if (components.isEmpty()) {
                components.add(next);
                return;
            }

            OTComponent last = components.get(components.size() - 1);
            if (last instanceof OTRetain(int lastCount) && next instanceof OTRetain(int nextCount)) {
                components.set(components.size() - 1, new OTRetain(lastCount + nextCount));
                return;
            }
            if (last instanceof OTDelete(int lastCount) && next instanceof OTDelete(int nextCount)) {
                components.set(components.size() - 1, new OTDelete(lastCount + nextCount));
                return;
            }
            if (last instanceof OTInsert(String lastText) && next instanceof OTInsert(String nextText)) {
                components.set(components.size() - 1, new OTInsert(lastText + nextText));
                return;
            }
            components.add(next);
        }

        public OTTextOperation build() {
            if (operationId == null || operationId.isBlank()) {
                throw new IllegalArgumentException("operationId is required");
            }
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalArgumentException("clientId is required");
            }
            if (baseRevision < 0) {
                throw new IllegalArgumentException("baseRevision must be >= 0");
            }
            return new OTTextOperation(operationId, clientId, baseRevision, Collections.unmodifiableList(components));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OTTextOperation that)) {
            return false;
        }
        return baseRevision == that.baseRevision
                && Objects.equals(operationId, that.operationId)
                && Objects.equals(clientId, that.clientId)
                && Objects.equals(components, that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationId, clientId, baseRevision, components);
    }

    @Override
    public String toString() {
        return "OTTextOperation{" +
                "operationId='" + operationId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", baseRevision=" + baseRevision +
                ", components=" + components +
                '}';
    }
}
