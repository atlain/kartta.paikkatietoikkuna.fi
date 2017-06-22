package flyway.ptimigration;

import fi.nls.oskari.domain.map.OskariLayer;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.map.layer.formatters.LayerJSONFormatterWMTS;
import fi.nls.oskari.service.OskariComponentManager;
import fi.nls.oskari.service.capabilities.CapabilitiesCacheService;
import fi.nls.oskari.service.capabilities.OskariLayerCapabilities;
import fi.nls.oskari.wmts.WMTSCapabilitiesParser;
import fi.nls.oskari.wmts.domain.WMTSCapabilities;
import fi.nls.oskari.wmts.domain.WMTSCapabilitiesLayer;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MKUOSMANEN on 22.6.2017.
 */
public class _V1_6__fix_and_repopulate_preparsed_layer_capabilities_for_layers implements JdbcMigration {
    private static final Logger LOG = LogFactory.getLogger(_V1_6__fix_and_repopulate_preparsed_layer_capabilities_for_layers.class);
    private static final CapabilitiesCacheService CAPABILITIES_SERVICE = OskariComponentManager.getComponentOfType(CapabilitiesCacheService.class);
    private static final WMTSCapabilitiesParser WMTSPARSER = new WMTSCapabilitiesParser();

    public void migrate(Connection connection)
            throws SQLException {
        List<OskariLayer> layers = getLayers(connection);

        LOG.info("Start generating prepopulated capabilities for layers - count:", layers.size());
        int progress = 0;
        for (OskariLayer layer : layers) {
            try {
                final String url = layer.getSimplifiedUrl(true);
                OskariLayerCapabilities caps = CAPABILITIES_SERVICE.getCapabilities(layer);

                if (caps != null) {
                    WMTSCapabilities parsed = WMTSPARSER.parseCapabilities(caps.getData());
                    if (parsed != null) {
                        WMTSCapabilitiesLayer capsLayer = parsed.getLayer(layer.getName());
                        if (capsLayer != null) {
                            JSONObject jscaps = LayerJSONFormatterWMTS.createCapabilitiesJSON(parsed, capsLayer);
                            if (jscaps != null) {
                                updateCapabilities(layer.getId(), jscaps, connection);
                            }else {
                                LOG.info("WMTSCapabilities json create failed - layer: ", layer.getName());
                            }
                        } else {
                            LOG.info("WMTSCapabilities layer parse failed - layer: ", layer.getName());
                        }
                    } else {
                        LOG.info("WMTSCapabilities capabilities parse failed - layer: ", layer.getName());
                    }
                } else {
                    LOG.info("WMTSCapabilities getCapabilities failed - layer: ", layer.getName());
                }
                progress++;
                LOG.info("Capabilities parsed:", progress, "/", layers.size());
            } catch (Exception e) {
                LOG.error(e, "Error getting capabilities for layer", layer);
            }
        }
    }

    private void updateCapabilities(int layerId, JSONObject capabilities, Connection conn)
            throws SQLException {
        final String sql = "UPDATE oskari_maplayer SET capabilities=? where id=?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, capabilities.toString(2));
            statement.setInt(2, layerId);
            statement.execute();
        } catch (JSONException ignored) {
        }
    }

    List<OskariLayer> getLayers(Connection conn)
            throws SQLException {
        List<OskariLayer> layers = new ArrayList<>();
        // only process karttamoottori.maanmittauslaitos.fi-layers
        final String sql = "SELECT id, url, type, name FROM oskari_maplayer WHERE url LIKE 'http://karttamoottori.maanmittauslaitos.fi%'";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OskariLayer layer = new OskariLayer();
                    layer.setId(rs.getInt("id"));
                    layer.setUrl(rs.getString("url"));
                    layer.setName(rs.getString("name"));
                    layer.setType(rs.getString("type"));
                    layers.add(layer);
                }
            }
        }
        return layers;
    }
}
