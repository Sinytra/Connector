package dev.su5ed.connector.service;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import dev.su5ed.connector.loader.ConnectorEarlyLoader;

import java.util.List;
import java.util.Set;

public class ConnectorLoaderService implements ITransformationService {
    private static final String NAME = "connector_loader";
    
    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        ConnectorEarlyLoader.setup();
        return List.of();
    }

    @Override
    public void initialize(IEnvironment environment) {}

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {}

    @Override
    public List<ITransformer> transformers() {
        return List.of();
    }
}
