package info.sigterm.deob.attributes.code.instructions;

import info.sigterm.deob.attributes.code.Instruction;
import info.sigterm.deob.attributes.code.InstructionType;
import info.sigterm.deob.attributes.code.Instructions;
import info.sigterm.deob.attributes.code.instruction.types.JumpingInstruction;
import info.sigterm.deob.execution.Frame;
import info.sigterm.deob.execution.InstructionContext;
import info.sigterm.deob.execution.Stack;
import info.sigterm.deob.execution.StackContext;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LookupSwitch extends Instruction implements JumpingInstruction
{
	private List<Instruction> branchi = new ArrayList<>();
	private Instruction defi;
	
	private int def;
	private int count;
	private int[] match;
	private int[] branch;

	public LookupSwitch(Instructions instructions, InstructionType type, int pc) throws IOException
	{
		super(instructions, type, pc);

		DataInputStream is = instructions.getCode().getAttributes().getStream();

		int tableSkip = 4 - (pc + 1) % 4;
		if (tableSkip == 4) tableSkip = 0;
		if (tableSkip > 0) is.skip(tableSkip);

		def = is.readInt();

		count = is.readInt();
		match = new int[count];
		branch = new int[count];

		for (int i = 0; i < count; ++i)
		{
			match[i] = is.readInt();
			branch[i] = is.readInt();
		}

		length += tableSkip + 8 + (count * 8);
	}
	
	@Override
	public void setPc(int pc)
	{
		super.setPc(pc);
		
		int tableSkip = 4 - (pc + 1) % 4;
		if (tableSkip == 4) tableSkip = 0;
		
		length = 1 + tableSkip + 8 + (count * 8);
	}
	
	@Override
	public void resolve()
	{
		for (int i : branch)
			branchi.add(this.getInstructions().findInstruction(this.getPc() + i));
		defi = this.getInstructions().findInstruction(this.getPc() + def);
	}
	
	@Override
	public void write(DataOutputStream out) throws IOException
	{
		super.write(out);
		
		int tableSkip = 4 - (this.getPc() + 1) % 4;
		if (tableSkip == 4) tableSkip = 0;
		if (tableSkip > 0) out.write(new byte[tableSkip]);
		
		out.writeInt(defi.getPc() - this.getPc());
		
		out.writeInt(count);
		for (int i = 0; i < count; ++i)
		{
			out.writeInt(match[i]);
			out.writeInt(branchi.get(i).getPc() - this.getPc());
		}
	}

	@Override
	public void buildJumpGraph()
	{
		for (Instruction i : branchi)
			this.addJump(i);
		this.addJump(defi);
	}

	@Override
	public void execute(Frame frame)
	{
		InstructionContext ins = new InstructionContext(this, frame);
		Stack stack = frame.getStack();
		
		StackContext value = stack.pop();
		ins.pop(value);
		
		frame.addInstructionContext(ins);
		
		for (int i : branch)
		{
			Frame other = frame.dup();
			other.jump(i);
		}
		
		frame.jump(def);
	}
	
	@Override
	public boolean isTerminal()
	{
		return true;
	}
	
	@Override
	public void replace(Instruction oldi, Instruction newi)
	{
		if (defi == oldi)
			defi = newi;
		
		for (int i = 0; i < branchi.size(); ++i)
			if (branchi.get(i) == oldi)
				branchi.set(i, newi);
	}
}
