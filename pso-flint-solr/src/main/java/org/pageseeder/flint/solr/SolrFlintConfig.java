package org.pageseeder.flint.solr;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrFlintConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(SolrFlintConfig.class);

  private static SolrFlintConfig INSTANCE;

  private final String _serverURL;

  private final String _zkhosts;

  private final File templatesFolder;

//  private final IndexManager _manager;

  public static void setup(File templates, String url) {
    setup(templates, url, null);
  }

  public static void setup(File templates, String url, String zkhosts) {
    if (INSTANCE == null) {
      INSTANCE = new SolrFlintConfig(templates, url, zkhosts);
      LOGGER.info("Solr Flint has been setup with Solr server at {} and templates under {}", INSTANCE._serverURL, INSTANCE.templatesFolder);
    } else {
      LOGGER.warn("Trying to setup Solr Flint twice, ignoring!");
    }
  }

  public static SolrFlintConfig getInstance() {
    if (INSTANCE == null) {
      LOGGER.warn("Solr Flint has not been setup, using default properties!");
      INSTANCE = new SolrFlintConfig(null, "http://localhost:8983/solr/", null);
    }
    return INSTANCE;
  }

  private SolrFlintConfig(File templates, String url, String zkhosts) {
    this.templatesFolder = templates;
    this._serverURL = url;
    this._zkhosts = zkhosts;
  }

  public String getServerURL() {
    return this._serverURL;
  }

  public String getZKHosts() {
    return this._zkhosts;
  }

  public File getTemplatesFolder() {
    return this.templatesFolder;
  }

}
