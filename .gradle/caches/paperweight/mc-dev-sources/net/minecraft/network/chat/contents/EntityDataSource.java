package net.minecraft.network.chat.contents;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.NbtPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public record EntityDataSource(String selectorPattern, @Nullable EntitySelector compiledSelector) implements DataSource {
    public static final MapCodec<EntityDataSource> SUB_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.STRING.fieldOf("entity").forGetter(EntityDataSource::selectorPattern)).apply(instance, EntityDataSource::new)
    );
    public static final DataSource.Type<EntityDataSource> TYPE = new DataSource.Type<>(SUB_CODEC, "entity");

    public EntityDataSource(String rawPath) {
        this(rawPath, compileSelector(rawPath));
    }

    @Nullable
    private static EntitySelector compileSelector(String rawSelector) {
        try {
            EntitySelectorParser entitySelectorParser = new EntitySelectorParser(new StringReader(rawSelector), true);
            return entitySelectorParser.parse();
        } catch (CommandSyntaxException var2) {
            return null;
        }
    }

    @Override
    public Stream<CompoundTag> getData(CommandSourceStack source) throws CommandSyntaxException {
        if (this.compiledSelector != null) {
            List<? extends Entity> list = this.compiledSelector.findEntities(source);
            return list.stream().map(NbtPredicate::getEntityTagToCompare);
        } else {
            return Stream.empty();
        }
    }

    @Override
    public DataSource.Type<?> type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "entity=" + this.selectorPattern;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof EntityDataSource entityDataSource && this.selectorPattern.equals(entityDataSource.selectorPattern)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.selectorPattern.hashCode();
    }
}
