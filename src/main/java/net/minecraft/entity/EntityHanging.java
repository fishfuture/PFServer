package net.minecraft.entity;

import com.google.common.base.Predicate;
import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.bukkit.entity.Hanging;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

import javax.annotation.Nullable;

public abstract class EntityHanging extends Entity
{
    private static final Predicate<Entity> IS_HANGING_ENTITY = new Predicate<Entity>()
    {
        public boolean apply(@Nullable Entity p_apply_1_)
        {
            return p_apply_1_ instanceof EntityHanging;
        }
    };
    private int tickCounter1;
    public BlockPos hangingPosition;
    @Nullable
    public EnumFacing facingDirection;

    public EntityHanging(World worldIn)
    {
        super(worldIn);
        this.setSize(0.5F, 0.5F);
    }

    public EntityHanging(World worldIn, BlockPos hangingPositionIn)
    {
        this(worldIn);
        this.hangingPosition = hangingPositionIn;
    }

    protected void entityInit()
    {
    }

    public void updateFacingWithBoundingBox(EnumFacing facingDirectionIn)
    {
        Validate.notNull(facingDirectionIn);
        Validate.isTrue(facingDirectionIn.getAxis().isHorizontal());
        this.facingDirection = facingDirectionIn;
        this.rotationYaw = (float)(this.facingDirection.getHorizontalIndex() * 90);
        this.prevRotationYaw = this.rotationYaw;
        this.updateBoundingBox();
    }

    protected void updateBoundingBox()
    {
        if (this.facingDirection != null)
        {
            // CraftBukkit start code moved in to calculateBoundingBox
            this.setEntityBoundingBox(calculateBoundingBox(this, this.hangingPosition, this.facingDirection, this.getWidthPixels(), this.getHeightPixels()));
            // CraftBukkit end
        }
    }

    // CraftBukkit start - break out BB calc into own method
    public static AxisAlignedBB calculateBoundingBox(Entity entity, BlockPos blockPosition, EnumFacing direction, int width, int height) {
        double d0 = (double) blockPosition.getX() + 0.5D;
        double d1 = (double) blockPosition.getY() + 0.5D;
        double d2 = (double) blockPosition.getZ() + 0.5D;
        double d3 = 0.46875D;
        double d4 = offs(width);
        double d5 = offs(height);

        d0 -= (double) direction.getFrontOffsetX() * 0.46875D;
        d2 -= (double) direction.getFrontOffsetZ() * 0.46875D;
        d1 += d5;
        EnumFacing enumdirection = direction.rotateYCCW();

        d0 += d4 * (double) enumdirection.getFrontOffsetX();
        d2 += d4 * (double) enumdirection.getFrontOffsetZ();
        if (entity != null) {
            entity.posX = d0;
            entity.posY = d1;
            entity.posZ = d2;
        }
        double d6 = (double) width;
        double d7 = (double) height;
        double d8 = (double) width;

        if (direction.getAxis() == EnumFacing.Axis.Z) {
            d8 = 1.0D;
        } else {
            d6 = 1.0D;
        }

        d6 /= 32.0D;
        d7 /= 32.0D;
        d8 /= 32.0D;
        return new AxisAlignedBB(d0 - d6, d1 - d7, d2 - d8, d0 + d6, d1 + d7, d2 + d8);
    }

    private static double offs(int p_190202_1_) // CraftBukkit - static
    {
        return p_190202_1_ % 32 == 0 ? 0.5D : 0.0D;
    }

    public void onUpdate()
    {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.tickCounter1++ == this.world.spigotConfig.hangingTickFrequency && !this.world.isRemote) // Spigot
        {
            this.tickCounter1 = 0;

            if (!this.isDead && !this.onValidSurface())
            {
                // CraftBukkit start - fire break events
                Material material = this.world.getBlockState(new BlockPos(this)).getMaterial();
                HangingBreakEvent.RemoveCause cause;

                if (!material.equals(Material.AIR)) {
                    // TODO: This feels insufficient to catch 100% of suffocation cases
                    cause = HangingBreakEvent.RemoveCause.OBSTRUCTION;
                } else {
                    cause = HangingBreakEvent.RemoveCause.PHYSICS;
                }

                HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), cause);
                this.world.getServer().getPluginManager().callEvent(event);

                if (isDead || event.isCancelled()) {
                    return;
                }
                // CraftBukkit end
                this.setDead();
                this.onBroken((Entity)null);
            }
        }
    }

    public boolean onValidSurface()
    {
        if (!this.world.getCollisionBoxes(this, this.getEntityBoundingBox()).isEmpty())
        {
            return false;
        }
        else
        {
            int i = Math.max(1, this.getWidthPixels() / 16);
            int j = Math.max(1, this.getHeightPixels() / 16);
            BlockPos blockpos = this.hangingPosition.offset(this.facingDirection.getOpposite());
            EnumFacing enumfacing = this.facingDirection.rotateYCCW();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (int k = 0; k < i; ++k)
            {
                for (int l = 0; l < j; ++l)
                {
                    int i1 = (i - 1) / -2;
                    int j1 = (j - 1) / -2;
                    blockpos$mutableblockpos.setPos(blockpos).move(enumfacing, k + i1).move(EnumFacing.UP, l + j1);
                    IBlockState iblockstate = this.world.getBlockState(blockpos$mutableblockpos);

                    if (iblockstate.isSideSolid(this.world, blockpos$mutableblockpos, this.facingDirection))
                        continue;

                    if (!iblockstate.getMaterial().isSolid() && !BlockRedstoneDiode.isDiode(iblockstate))
                    {
                        return false;
                    }
                }
            }

            return this.world.getEntitiesInAABBexcluding(this, this.getEntityBoundingBox(), IS_HANGING_ENTITY).isEmpty();
        }
    }

    public boolean canBeCollidedWith()
    {
        return true;
    }

    public boolean hitByEntity(Entity entityIn)
    {
        return entityIn instanceof EntityPlayer ? this.attackEntityFrom(DamageSource.causePlayerDamage((EntityPlayer)entityIn), 0.0F) : false;
    }

    public EnumFacing getHorizontalFacing()
    {
        return this.facingDirection;
    }

    public boolean attackEntityFrom(DamageSource source, float amount)
    {
        if (this.isEntityInvulnerable(source))
        {
            return false;
        }
        else
        {
            if (!this.isDead && !this.world.isRemote)
            {
                // CraftBukkit start - fire break events
                HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), HangingBreakEvent.RemoveCause.DEFAULT);
                if (source.getTrueSource() != null) {
                    event = new HangingBreakByEntityEvent((Hanging) this.getBukkitEntity(), source.getTrueSource() == null ? null : source.getTrueSource().getBukkitEntity(), source.isExplosion() ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.ENTITY);
                } else if (source.isExplosion()) {
                    event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), HangingBreakEvent.RemoveCause.EXPLOSION);
                }

                this.world.getServer().getPluginManager().callEvent(event);

                if (this.isDead || event.isCancelled()) {
                    return true;
                }
                // CraftBukkit end
                this.setDead();
                this.markVelocityChanged();
                this.onBroken(source.getTrueSource());
            }

            return true;
        }
    }

    public void move(MoverType type, double x, double y, double z)
    {
        if (!this.world.isRemote && !this.isDead && x * x + y * y + z * z > 0.0D)
        {
            // CraftBukkit start - fire break events
            // TODO - Does this need its own cause? Seems to only be triggered by pistons
            HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), HangingBreakEvent.RemoveCause.PHYSICS);
            this.world.getServer().getPluginManager().callEvent(event);

            if (this.isDead || event.isCancelled()) {
                return;
            }
            // CraftBukkit end
            this.setDead();
            this.onBroken((Entity)null);
        }
    }

    public void addVelocity(double x, double y, double z)
    {
        if (false && !this.world.isRemote && !this.isDead && x * x + y * y + z * z > 0.0D)
        {
            this.setDead();
            this.onBroken((Entity)null);
        }
    }

    public void writeEntityToNBT(NBTTagCompound compound)
    {
        compound.setByte("Facing", (byte)this.facingDirection.getHorizontalIndex());
        BlockPos blockpos = this.getHangingPosition();
        compound.setInteger("TileX", blockpos.getX());
        compound.setInteger("TileY", blockpos.getY());
        compound.setInteger("TileZ", blockpos.getZ());
    }

    public void readEntityFromNBT(NBTTagCompound compound)
    {
        this.hangingPosition = new BlockPos(compound.getInteger("TileX"), compound.getInteger("TileY"), compound.getInteger("TileZ"));
        this.updateFacingWithBoundingBox(EnumFacing.getHorizontal(compound.getByte("Facing")));
    }

    public abstract int getWidthPixels();

    public abstract int getHeightPixels();

    public abstract void onBroken(@Nullable Entity brokenEntity);

    public abstract void playPlaceSound();

    public EntityItem entityDropItem(ItemStack stack, float offsetY)
    {
        EntityItem entityitem = new EntityItem(this.world, this.posX + (double)((float)this.facingDirection.getFrontOffsetX() * 0.15F), this.posY + (double)offsetY, this.posZ + (double)((float)this.facingDirection.getFrontOffsetZ() * 0.15F), stack);
        entityitem.setDefaultPickupDelay();
        this.world.spawnEntity(entityitem);
        return entityitem;
    }

    protected boolean shouldSetPosAfterLoading()
    {
        return false;
    }

    public void setPosition(double x, double y, double z)
    {
        this.hangingPosition = new BlockPos(x, y, z);
        this.updateBoundingBox();
        this.isAirBorne = true;
    }

    public BlockPos getHangingPosition()
    {
        return this.hangingPosition;
    }

    @SuppressWarnings("incomplete-switch")
    public float getRotatedYaw(Rotation transformRotation)
    {
        if (this.facingDirection != null && this.facingDirection.getAxis() != EnumFacing.Axis.Y)
        {
            switch (transformRotation)
            {
                case CLOCKWISE_180:
                    this.facingDirection = this.facingDirection.getOpposite();
                    break;
                case COUNTERCLOCKWISE_90:
                    this.facingDirection = this.facingDirection.rotateYCCW();
                    break;
                case CLOCKWISE_90:
                    this.facingDirection = this.facingDirection.rotateY();
            }
        }

        float f = MathHelper.wrapDegrees(this.rotationYaw);

        switch (transformRotation)
        {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 90.0F;
            case CLOCKWISE_90:
                return f + 270.0F;
            default:
                return f;
        }
    }

    public float getMirroredYaw(Mirror transformMirror)
    {
        return this.getRotatedYaw(transformMirror.toRotation(this.facingDirection));
    }

    public void onStruckByLightning(@Nullable EntityLightningBolt lightningBolt)
    {
    }
}