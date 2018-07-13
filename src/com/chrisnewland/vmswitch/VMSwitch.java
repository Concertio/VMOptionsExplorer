/*
 * Copyright (c) 2018 Chris Newland.
 * Licensed under https://github.com/chriswhocodes/VMSwitch/blob/master/LICENSE
 */
package com.chrisnewland.vmswitch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.chrisnewland.vmswitch.parser.GraalSwitchParser;
import com.chrisnewland.vmswitch.parser.HotSpotSwitchParser;
import com.chrisnewland.vmswitch.parser.ISwitchParser;
import com.chrisnewland.vmswitch.parser.OpenJ9SwitchParser;

import java.util.Set;
import java.util.HashSet;
import java.util.Date;
import java.util.HashMap;

public class VMSwitch
{
	private File vmPath;

	private List<VMData> vmDataList = new ArrayList<>();

	private Map<String, String> switchNameVersions = new HashMap<>();

	public VMSwitch()
	{
	}

	public void addVM(VMData data)
	{
		vmDataList.add(data);
	}

	public void process() throws Exception
	{
		int count = vmDataList.size();

		for (int i = 0; i < count; i++)
		{
			parseJDK(i, vmDataList.get(i));
		}
	}

	public void processVMDeltas(VMType vmType) throws IOException
	{
		StringBuilder builder = new StringBuilder();

		List<VMData> vmsOfType = getVMsOfType(vmType);

		int count = vmsOfType.size();

		if (count > 1)
		{
			for (int i = 0; i < count - 1; i++)
			{
				VMData earlier = vmsOfType.get(i);
				VMData later = vmsOfType.get(i + 1);

				addChangesBetweenVMs(earlier, later, builder);
			}
		}

		String template = new String(Files.readAllBytes(Paths.get("html/template_delta.html")), StandardCharsets.UTF_8);

		template = template.replace("$DELTA_BODY", builder.toString());
		template = template.replace("$DATE", new Date().toString());

		Files.write(Paths.get("html/hotspot_option_differences.html"), template.getBytes(StandardCharsets.UTF_8));
	}

	private void addChangesBetweenVMs(VMData earlier, VMData later, StringBuilder builder) throws IOException
	{
		System.out.println("Calculating changes between " + earlier.getJdkName() + " and " + later.getJdkName());

		Map<String, SwitchInfo> switchMapEarlier = getParser(earlier.getVmType()).process(earlier.getVmPath());

		Map<String, SwitchInfo> switchMapLater = getParser(later.getVmType()).process(later.getVmPath());

		Set<String> namesEarlier = new HashSet<>();

		Set<String> namesLater = new HashSet<>();

		Set<String> check = new HashSet<>();
		// check.add("VerboseVerification");

		for (Map.Entry<String, SwitchInfo> entry : switchMapEarlier.entrySet())
		{
			String switchName = entry.getValue().getName();

			if (check.contains(switchName))
			{
				System.out.println(earlier.getJdkName() + " has " + switchName);
			}

			namesEarlier.add(switchName);
		}

		for (Map.Entry<String, SwitchInfo> entry : switchMapLater.entrySet())
		{
			String switchName = entry.getValue().getName();

			if (check.contains(switchName))
			{
				System.out.println(later.getJdkName() + " has " + switchName);
			}

			namesLater.add(switchName);
		}

		builder
				.append("<h2>")
				.append("Differences between ")
				.append(earlier.getJdkName())
				.append(" and ")
				.append(later.getJdkName())
				.append("</h2>");

		DeltaTable deltaTable = new DeltaTable(earlier, later);

		for (String inEarlier : namesEarlier)
		{
			if (!namesLater.contains(inEarlier))
			{
				deltaTable.recordRemoval(inEarlier);
			}
		}

		for (String inLater : namesLater)
		{
			if (!namesEarlier.contains(inLater))
			{
				deltaTable.recordAddition(inLater);
			}
		}
		
		System.out.println("Removed " + deltaTable.getRemovalCount() + " Added " + deltaTable.getAdditionCount());

		builder.append(deltaTable.toString());
	}

	private List<VMData> getVMsOfType(VMType vmType)
	{
		List<VMData> result = new ArrayList<>();

		for (VMData vmData : vmDataList)
		{
			if (vmData.getVmType() == vmType)
			{
				result.add(vmData);
			}
		}

		Collections.sort(result, new Comparator<VMData>()
		{
			@Override
			public int compare(VMData vmd1, VMData vmd2)
			{
				int version1 = getVersionFromJDKName(vmd1.getJdkName());
				int version2 = getVersionFromJDKName(vmd2.getJdkName());

				return Integer.compare(version1, version2);
			}
		});

		return result;
	}

	private int getVersionFromJDKName(String name)
	{
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < name.length(); i++)
		{
			char c = name.charAt(i);

			if (Character.isDigit(c))
			{
				builder.append(c);
			}
		}

		int result = 0;

		try
		{
			result = Integer.parseInt(builder.toString());
		}
		catch (NumberFormatException nfe)
		{
			nfe.printStackTrace();
		}

		return result;
	}

	private ISwitchParser getParser(VMType vmType)
	{
		switch (vmType)
		{
		case GRAAL:
			return new GraalSwitchParser();
		case OPENJ9:
			return new OpenJ9SwitchParser();
		case HOTSPOT:
			return new HotSpotSwitchParser();
		default:
			throw new RuntimeException("Unexpected VM Type: " + vmType);
		}
	}

	private String getVMDisplayName(VMType vmType)
	{
		switch (vmType)
		{
		case GRAAL:
			return "Graal";
		case OPENJ9:
			return "OpenJ9";
		case HOTSPOT:
			return "HotSpot";
		default:
			throw new RuntimeException("Unexpected VM Type: " + vmType);
		}
	}

	private void parseJDK(int jdkIndex, VMData vmData) throws IOException
	{
		this.vmPath = vmData.getVmPath();

		String jdkName = vmData.getJdkName();

		VMType vmType = vmData.getVmType();

		String vmName = getVMDisplayName(vmData.getVmType());

		ISwitchParser switchParser = getParser(vmData.getVmType());

		Map<String, SwitchInfo> switchMap = switchParser.process(vmPath);

		String template = new String(Files.readAllBytes(Paths.get("html/template.html")), StandardCharsets.UTF_8);

		StringBuilder htmlBuilder = new StringBuilder();

		Set<String> switchNames = new HashSet<>();

		for (Map.Entry<String, SwitchInfo> entry : switchMap.entrySet())
		{
			SwitchInfo info = entry.getValue();

			String switchName = info.getName();

			// System.out.println(info);

			if (vmType == VMType.HOTSPOT)
			{
				String firstSeenInJDK = switchNameVersions.get(switchName);

				if (firstSeenInJDK == null)
				{
					firstSeenInJDK = jdkName;

					switchNameVersions.put(switchName, firstSeenInJDK);
				}

				info.setSince(firstSeenInJDK);
			}

			htmlBuilder.append(info.toRow(vmType)).append("\n");

			switchNames.add(switchName);
		}

		template = template.replace("$THEAD", SwitchInfo.getHeaderRow(vmType));
		template = template.replace("$VMNAME", vmName);
		template = template.replace("$JDK", jdkName);
		template = template.replace("$COUNT", Integer.toString(switchNames.size()));
		template = template.replace("$DATE", new Date().toString());
		template = template.replace("$TBODY", htmlBuilder.toString());

		String outputFilename = "html/" + vmData.getHTMLFilename();

		switch (vmData.getVmType())
		{
		case GRAAL:
			template = template.replace("$TOPHEADER", "<th></th><th>Type</th><th></th><th></th>");
			template = template.replace("$ALLCOLUMNS", "[ 0,1,2,3 ]");
			template = template.replace("$SORTCOLUMNS", "[ 1 ]");
			break;
		case OPENJ9:
			template = template.replace("$TOPHEADER", "<th></th><th></th>");
			template = template.replace("$ALLCOLUMNS", "[ 0,1 ]");
			template = template.replace("$SORTCOLUMNS", "[ ]");
			break;
		case HOTSPOT:
			template = template
								.replace("$TOPHEADER",
										"<th></th><th>Since</th><th>Type</th><th>OS</th><th>CPU</th><th>Component</th><th></th><th>Availability</th><th></th><th></th>");
			template = template.replace("$ALLCOLUMNS", "[ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 ]");
			template = template.replace("$SORTCOLUMNS", "[ 1, 2, 3, 4, 5, 7 ]");
			break;
		}

		Files.write(Paths.get(outputFilename), template.getBytes(StandardCharsets.UTF_8));

		System.out.println(jdkName + " Count: " + switchNames.size());
	}

	public static void main(String[] args) throws Exception
	{
		VMSwitch vms = new VMSwitch();

		vms.addVM(new VMData("JDK6", new File("/home/chris/openjdk/jdk6/hotspot"), VMType.HOTSPOT));
		vms.addVM(new VMData("JDK7", new File("/home/chris/openjdk/jdk7u/hotspot"), VMType.HOTSPOT));
		vms.addVM(new VMData("JDK8", new File("/home/chris/openjdk/jdk8u/hotspot"), VMType.HOTSPOT));
		vms.addVM(new VMData("JDK9", new File("/home/chris/openjdk/jdk9-dev/hotspot"), VMType.HOTSPOT));
		vms.addVM(new VMData("JDK10", new File("/home/chris/openjdk/jdk10/src/hotspot"), VMType.HOTSPOT));
		vms.addVM(new VMData("JDK11", new File("/home/chris/openjdk/jdk11/src/hotspot"), VMType.HOTSPOT));

		// Generate these files with -XX:+JVMCIPrintProperties
		vms.addVM(new VMData("Graal CE 1.0", new File("/home/chris/openjdk/VMSwitch/graal_ce.out"), VMType.GRAAL));
		vms.addVM(new VMData("Graal EE 1.0", new File("/home/chris/openjdk/VMSwitch/graal_ee.out"), VMType.GRAAL));

		vms.addVM(new VMData("OpenJ9", new File("/home/chris/openjdk/openj9"), VMType.OPENJ9));

		vms.process();

		vms.processVMDeltas(VMType.HOTSPOT);
	}
}