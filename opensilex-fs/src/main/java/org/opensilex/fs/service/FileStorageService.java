//******************************************************************************
//                         FileStorageService.java
// OpenSILEX - Licence AGPL V3.0 - https://www.gnu.org/licenses/agpl-3.0.en.html
// Copyright © INRA 2019
// Contact: vincent.migot@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
//******************************************************************************
package org.opensilex.fs.service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensilex.config.InvalidConfigException;
import org.opensilex.service.BaseService;
import org.opensilex.service.Service;
import org.opensilex.service.ServiceDefaultDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.logging.Level;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.opensilex.fs.local.TempFileSystemConnection;

/**
 * File storage service to access any filesystem (default to local)
 *
 * @author Vincent Migot
 */
@ServiceDefaultDefinition(config = FileStorageServiceConfig.class)
public class FileStorageService extends BaseService implements Service {

    private final static Logger LOGGER = LoggerFactory.getLogger(FileStorageService.class);

    public final static String DEFAULT_FS_SERVICE = "fs";

    private final FileStorageConnection defaultFS;

    private final Map<Path, FileStorageConnection> customPath = new HashMap<>();

    private final List<Path> pathOrder;

    private final Map<String, FileStorageConnection> connections;

    public FileStorageService(FileStorageServiceConfig config) throws InvalidConfigException {
        super(config);
        connections = config.connections();
        for (Map.Entry<String, String> path : config.customPath().entrySet()) {
            Path pathPrefix = Paths.get(path.getKey());
            String connectionID = path.getValue();

            if (!connections.containsKey(connectionID)) {
                throw new InvalidConfigException("File storage connection not found: " + connectionID);
            }

            customPath.put(pathPrefix, connections.get(connectionID));
        }

        pathOrder = new ArrayList<>(customPath.keySet());

        pathOrder.sort((Path p1, Path p2) -> {
            if (p1 == null) {
                if (p2 == null) {
                    return 0;
                } else {
                    return -1;
                }
            } else {
                if (p2 == null) {
                    return 1;
                } else if (p1.toAbsolutePath().equals(p2.toAbsolutePath())) {
                    return 0;
                } else if (p1.toAbsolutePath().startsWith(p2.toAbsolutePath())) {
                    return 1;
                } else if (p2.toAbsolutePath().startsWith(p1.toAbsolutePath())) {
                    return -1;
                } else {
                    return p1.toAbsolutePath().compareTo(p2.toAbsolutePath());
                }
            }
        });
        String tmpDefaultFS = (StringUtils.isBlank(config.defaultFS()) ? null : config.defaultFS());

        if (!connections.containsKey(tmpDefaultFS)) {
            LOGGER.info("File storage connection not found: " + tmpDefaultFS + " Default temporary file system will be used on configured mongo server");
        }
        if (tmpDefaultFS == null) {
            TempFileSystemConnection tempFileSystemConnection = null;
            try {
                tempFileSystemConnection = new TempFileSystemConnection();
            } catch (IOException ex) {
                 LOGGER.error(ex.getMessage(),ex);
            }
            this.defaultFS = tempFileSystemConnection;
        } else {
            this.defaultFS = connections.get(tmpDefaultFS);
        }
        if(this.defaultFS == null){
            throw new InvalidConfigException("File storage connection not set");
        }
    }

    @Override
    public void setup() throws Exception {
        if (defaultFS != null) {
            defaultFS.setOpenSilex(getOpenSilex());
            defaultFS.setup();
        }
        for (FileStorageConnection connection : connections.values()) {
            if (connection != null) {
                connection.setOpenSilex(getOpenSilex());
                connection.setup();
            }
        }
    }

    @Override
    public void clean() throws Exception {
        if (defaultFS != null) {
            defaultFS.clean();
        }

        for (FileStorageConnection connection : connections.values()) {
            if (connection != null) {
                connection.clean();
            }
        }
    }

    @Override
    public void startup() throws Exception {
        if (defaultFS != null) {
            defaultFS.startup();
        }
        for (FileStorageConnection connection : connections.values()) {
            if (connection != null) {
                connection.startup();
            }
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (defaultFS != null) {
            defaultFS.shutdown();
        }
        for (FileStorageConnection connection : connections.values()) {
            if (connection != null) {
                connection.shutdown();
            }
        }
    }

    public FileStorageServiceConfig getImplementedConfig() {
        return (FileStorageServiceConfig) this.getConfig();
    }

    protected FileStorageConnection getConnection(String prefix) {
        for (Path candidatePath : pathOrder) {
            if (prefix.equals(candidatePath.toString())) {
                return customPath.get(candidatePath);
            }
        }

        return this.defaultFS;
    }

    public Path getAbsolutePath(String prefix, Path filePath) throws IOException {
        return this.getConnection(prefix).getAbsolutePath(filePath);
    }

    public String readFile(String prefix, Path filePath) throws IOException {
        LOGGER.debug("READ FILE: " + filePath.toString());
        return getConnection(prefix).readFile(filePath);
    }

    public void writeFile(String prefix, Path filePath, String content, URI fileURI) throws IOException {
        LOGGER.debug("WRITE FILE: " + filePath.toString());
        getConnection(prefix).writeFile(filePath, content);
    }

    public void writeFile(String prefix, Path filePath, File file, URI fileURI) throws IOException {
        LOGGER.debug("WRITE FILE: " + filePath.toString());
        getConnection(prefix).writeFile(filePath, file);
    }

    public void createDirectories(String prefix, Path directoryPath) throws IOException {
        LOGGER.debug("CREATE DIRECTORIES: " + directoryPath.toString());
        getConnection(prefix).createDirectories(directoryPath);
    }

    public byte[] readFileAsByteArray(String prefix, Path filePath) throws IOException {
        LOGGER.debug("READ FILE BYTES: " + filePath.toString());
        return getConnection(prefix).readFileAsByteArray(filePath);
    }

    public boolean exist(String prefix, Path filePath) throws IOException {
        LOGGER.debug("TEST FILE EXISTENCE: " + filePath.toString());
        return getConnection(prefix).exist(filePath);
    }

    public void delete(String prefix, Path filePath) throws IOException {
        LOGGER.debug("DELETE FILE: " + filePath.toString());
        getConnection(prefix).delete(filePath);
    }

    public Path getFilePathFromPrefixURI(String prefix, URI fileURI) {
        return Paths.get(prefix, fileURI.getPath(), Hex.encodeHexString(fileURI.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public String readFile(String prefix, URI fileURI) throws IOException {
        return readFile(prefix, getFilePathFromPrefixURI(prefix, fileURI));
    }

    public void writeFile(String prefix, URI fileURI, String content) throws IOException {
        writeFile(prefix, getFilePathFromPrefixURI(prefix, fileURI), content, fileURI);
    }

    public void writeFile(String prefix, URI fileURI, File file) throws IOException {
        Path filePath = getFilePathFromPrefixURI(prefix, fileURI);
        try {
            createDirectories(prefix, filePath.getParent());
        } catch (IOException e) {
            LOGGER.debug(e.getMessage());
        }
        getConnection(prefix).writeFile(filePath, file);
    }
    
    public void writeFile(String prefix, Path filePath, File file) throws IOException {
        getConnection(prefix).writeFile(filePath, file);
    }

    public byte[] readFileAsByteArray(String prefix, URI fileURI) throws IOException {
        return readFileAsByteArray(prefix, getFilePathFromPrefixURI(prefix, fileURI));
    }

    public boolean exist(String prefix, URI fileURI) throws IOException {
        return getConnection(prefix).exist(getFilePathFromPrefixURI(prefix, fileURI));
    }

    public void delete(String prefix, URI fileURI) throws IOException {
        getConnection(prefix).delete(getFilePathFromPrefixURI(prefix, fileURI));
    }

    public void deleteIfExists(String prefix, Path file) {
        try {
            getConnection(prefix).delete(file);
        } catch (Exception e) {
        }
    }
}
