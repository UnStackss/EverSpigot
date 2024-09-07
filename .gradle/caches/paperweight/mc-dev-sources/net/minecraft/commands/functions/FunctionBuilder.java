package net.minecraft.commands.functions;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.resources.ResourceLocation;

class FunctionBuilder<T extends ExecutionCommandSource<T>> {
    @Nullable
    private List<UnboundEntryAction<T>> plainEntries = new ArrayList<>();
    @Nullable
    private List<MacroFunction.Entry<T>> macroEntries;
    private final List<String> macroArguments = new ArrayList<>();

    public void addCommand(UnboundEntryAction<T> action) {
        if (this.macroEntries != null) {
            this.macroEntries.add(new MacroFunction.PlainTextEntry<>(action));
        } else {
            this.plainEntries.add(action);
        }
    }

    private int getArgumentIndex(String variable) {
        int i = this.macroArguments.indexOf(variable);
        if (i == -1) {
            i = this.macroArguments.size();
            this.macroArguments.add(variable);
        }

        return i;
    }

    private IntList convertToIndices(List<String> variables) {
        IntArrayList intArrayList = new IntArrayList(variables.size());

        for (String string : variables) {
            intArrayList.add(this.getArgumentIndex(string));
        }

        return intArrayList;
    }

    public void addMacro(String command, int lineNum, T source) {
        StringTemplate stringTemplate = StringTemplate.fromString(command, lineNum);
        if (this.plainEntries != null) {
            this.macroEntries = new ArrayList<>(this.plainEntries.size() + 1);

            for (UnboundEntryAction<T> unboundEntryAction : this.plainEntries) {
                this.macroEntries.add(new MacroFunction.PlainTextEntry<>(unboundEntryAction));
            }

            this.plainEntries = null;
        }

        this.macroEntries.add(new MacroFunction.MacroEntry<>(stringTemplate, this.convertToIndices(stringTemplate.variables()), source));
    }

    public CommandFunction<T> build(ResourceLocation id) {
        return (CommandFunction<T>)(this.macroEntries != null
            ? new MacroFunction<>(id, this.macroEntries, this.macroArguments)
            : new PlainTextFunction<>(id, this.plainEntries));
    }
}
