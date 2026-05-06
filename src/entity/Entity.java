package entity;

import environment.RendererManager;
import lang.GeomMath;
import lang.Mat4;
import mesh.Material;
import mesh.Mesh;
import move.Move;
import move.MoveType;

public class Entity {
    public final int id;

    public float x, y, z;
    public float rotX, rotY, rotZ;
    public float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
    public float isOccluder = 0.0f; // 1.0f for Walls, 0.0f for everything else

    public Material material = new Material();
    public final Mat4 modelMatrix = new Mat4();
    public boolean isDirty = true;

    // --- ZERO-GC ANIMATION TRACKERS ---
    public final Move posMove = new Move();
    public final Move rotMove = new Move();
    public final Move scaleMove = new Move();


    public Entity(Mesh mesh, int textureId) {
        this.id = RendererManager.createEntity();
        RendererManager.meshIds.set(id, mesh.vaoId);
        RendererManager.diffuseTextureIds.set(id, textureId);
        this.material.diffuseTextureId = textureId;
    }

    public void setPosition(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        isDirty = true;
    }
    public void setRotation(float rx, float ry, float rz) {
        this.rotX = rx; this.rotY = ry; this.rotZ = rz;
        isDirty = true;
    }

    // ---> NEW: 3D Scale Setters <---
    public void setScale(float s) {
        this.scaleX = s; this.scaleY = s; this.scaleZ = s;
        isDirty = true;
    }
    public void setScale(float x, float y, float z) {
        this.scaleX = x; this.scaleY = y; this.scaleZ = z;
        isDirty = true;
    }

    // --- ANIMATION API ---

    public void move(int moveTypeCurve, int moveType, float destX, float destY, float destZ, float duration) {
        float ex = moveType == MoveType.STUDS ? x + destX : destX;
        float ey = moveType == MoveType.STUDS ? y + destY : destY;
        float ez = moveType == MoveType.STUDS ? z + destZ : destZ;
        posMove.start(moveTypeCurve, x, y, z, ex, ey, ez, duration);
    }

    public void moveRotate(int moveTypeCurve, int moveType, float destX, float destY, float destZ, float duration) {
        float ex = moveType == MoveType.STUDS ? rotX + destX : destX;
        float ey = moveType == MoveType.STUDS ? rotY + destY : destY;
        float ez = moveType == MoveType.STUDS ? rotZ + destZ : destZ;
        rotMove.start(moveTypeCurve, rotX, rotY, rotZ, ex, ey, ez, duration);
    }

    public void moveScale(int moveTypeCurve, int moveType, float destScale, float duration) {
        float ex = moveType == MoveType.STUDS ? scaleX + destScale : destScale;
        scaleMove.start(moveTypeCurve, scaleX, 0, 0, ex, 0, 0, duration);
    }

    // Backwards compatibility for your current Main.java loop
    public void moveRotate(float rx, float ry, float rz, float duration) {
        moveRotate(Move.NORMAL, MoveType.STUDS, rx, ry, rz, duration);
    }

    public void update(float delta) {
        // Position
        if (posMove.active) {
            if (posMove.duration == -1f) {
                this.x += posMove.endX * delta;
                this.y += posMove.endY * delta;
                this.z += posMove.endZ * delta;
                isDirty = true;
            } else {
                posMove.time += delta;
                float t = Math.min(posMove.time / posMove.duration, 1.0f);
                float eased = posMove.getEased(t);
                this.x = posMove.startX + (posMove.endX - posMove.startX) * eased;
                this.y = posMove.startY + (posMove.endY - posMove.startY) * eased;
                this.z = posMove.startZ + (posMove.endZ - posMove.startZ) * eased;
                isDirty = true;
                if (t >= 1.0f) posMove.active = false;
            }
        }

        // Rotation
        if (rotMove.active) {
            if (rotMove.duration == -1f) {
                this.rotX += rotMove.endX * delta;
                this.rotY += rotMove.endY * delta;
                this.rotZ += rotMove.endZ * delta;
                isDirty = true;
            } else {
                rotMove.time += delta;
                float t = Math.min(rotMove.time / rotMove.duration, 1.0f);
                float eased = rotMove.getEased(t);
                this.rotX = rotMove.startX + (rotMove.endX - rotMove.startX) * eased;
                this.rotY = rotMove.startY + (rotMove.endY - rotMove.startY) * eased;
                this.rotZ = rotMove.startZ + (rotMove.endZ - rotMove.startZ) * eased;
                isDirty = true;
                if (t >= 1.0f) rotMove.active = false;
            }
        }

        // Update Scale
        if (scaleMove.active) {
            scaleMove.time += delta;
            float t = Math.min(scaleMove.time / scaleMove.duration, 1.0f);
            float eased = scaleMove.getEased(t);
            float newScale = scaleMove.startX + (scaleMove.endX - scaleMove.startX) * eased;
            this.scaleX = newScale; this.scaleY = newScale; this.scaleZ = newScale;
            isDirty = true;
            if (t >= 1.0f) scaleMove.active = false;
        }

        if (isDirty) {
            GeomMath.createTransformationMatrix(x, y, z, rotX, rotY, rotZ, scaleX, scaleY, scaleZ, modelMatrix);
            isDirty = false;
        }
    }
}