package org.sinytra.connector.util;

import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;

import java.util.List;

public class PriorityModLoadingException extends ModLoadingException {
    public PriorityModLoadingException(ModLoadingIssue issue) {
        super(issue);
    }

    public PriorityModLoadingException(List<ModLoadingIssue> issues) {
        super(issues);
    }
}
