<template>
  <div>
    <opensilex-SelectForm
      :label="property.name"
      :selected.sync="internalValue"
      :multiple="property.is_list"
      :required="property.is_required"
      :searchMethod="searchParents"
      :itemLoadingMethod="getParentsByURI"
      placeholder="ScientificObjectParentPropertySelector.parent-placeholder"
    ></opensilex-SelectForm>
  </div>
</template>

<script lang="ts">
import {
  Component,
  Prop,
  Model,
  Provide,
  PropSync,
  Watch,
} from "vue-property-decorator";
import Vue from "vue";

@Component
export default class ScientificObjectParentPropertySelector extends Vue {
  $opensilex: any;

  @Prop()
  property;

  @PropSync("value")
  internalValue;

  @Prop()
  context;

  getContextURI() {
    if (this.context && this.context.experimentURI) {
      return this.context.experimentURI;
    } else {
      return undefined;
    }
  }

  searchParents(query, page, pageSize) {
    return this.$opensilex
      .getService("opensilex.ScientificObjectsService")
      .searchScientificObjects(
        this.getContextURI(),
        undefined,
        query,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        [],
        page,
        pageSize
      )
      .then((http) => {
        let nodeList = [];
        for (let so of http.response.result) {
          nodeList.push({
            id: so.uri,
            label: so.name + " (" + so.rdf_type_name + ")",
          });
        }
        http.response.result = nodeList;
        return http;
      });
  }

  getParentsByURI(soURIs) {
    let contextURI = undefined;
    if (this.context && this.context.experimentURI) {
      contextURI = this.context.experimentURI;
    }
    return this.$opensilex
      .getService("opensilex.ScientificObjectsService")
      .getScientificObjectsListByUris(contextURI, soURIs)
      .then((http) => {
      
        let nodeList = [];
        for (let so of http.response.result) {
          nodeList.push({
            id: so.uri,
            label: so.name + " (" + so.rdf_type_name + ")",
          });
        }
        return nodeList;
      });
  }
}
</script>

<style scoped lang="scss">
</style>


<i18n>
en:
  ScientificObjectParentPropertySelector:
    label: Facilities
    placeholder: Select a facility
    parent-placeholder: Select a scientific object

fr:
  ScientificObjectParentPropertySelector:
    label: Installation environnementale
    placeholder: Sélectionner une installation environnementale
    parent-placeholder: Sélectionner un objet scientifique
</i18n>