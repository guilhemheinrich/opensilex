<template>
  <div class="row">
    <div class="col col-xl-12">
      <opensilex-Card icon>
        <template v-slot:header>
          <opensilex-CreateButton
            v-if="
              user.hasCredential(credentials.CREDENTIAL_DATA_MODIFICATION_ID)
            "
            @click="dataForm.showCreateForm()"
            label="OntologyCsvImporter.import"
          ></opensilex-CreateButton>
        </template>
        <template v-slot:body>
          <opensilex-SearchFilterField
            ref="searchField"
            :withButton="true"
            label="DataView.filter.label"
            @search="refresh()"
            @clear="clear()"
          >
            <template v-slot:filters>
              <!-- Variables -->
              <opensilex-FilterField halfWidth="true">
                <opensilex-UsedVariableSelector
                  label="DataView.filter.variables"
                  :multiple="true"
                  :variables.sync="filter.variables"
                  :experiment="uri"
                  :key="refreshKey"
                ></opensilex-UsedVariableSelector>
              </opensilex-FilterField>

                <opensilex-FilterField quarterWidth="true">
                <!-- Start Date -->
                <opensilex-DateTimeForm
                  :value.sync="filter.start_date"
                  label="component.common.begin"
                  name="startDate"
                  :max-date="filter.end_date ? filter.end_date : undefined"
                ></opensilex-DateTimeForm>
              </opensilex-FilterField>

              <opensilex-FilterField quarterWidth="true">
                <!-- End Date -->
                <opensilex-DateTimeForm
                  :value.sync="filter.end_date"
                  label="component.common.end"
                  name="endDate"
                  :min-date="filter.start_date ? filter.start_date : undefined"
                ></opensilex-DateTimeForm>
              </opensilex-FilterField>

              <!-- Scientific objects -->
              <opensilex-FilterField halfWidth="true">
                <opensilex-SelectForm
                  ref="soSelector"
                  label="DataView.filter.scientificObjects"
                  placeholder="DataView.filter.scientificObjects-placeholder"
                  :selected.sync="filter.scientificObjects"
                  modalComponent="opensilex-ScientificObjectModalListByExp"
                  :filter.sync="soFilter"
                  :isModalSearch="true"
                  :clearable="true"
                  :multiple="true"
                  @clear="refreshSoSelector"
                  @onValidate="refreshProvComponent"
                  @onClose="refreshProvComponent"
                  :limit="1"
                ></opensilex-SelectForm>
              </opensilex-FilterField>

                   <!-- targets -->
              <opensilex-FilterField halfWidth="true">
                <opensilex-TagInputForm
                  class="overflow-auto"
                  style="height: 90px"
                  :value.sync="filter.targets"
                  label="DataView.filter.targets"
                  helpMessage="DataView.filter.targets-help"
                  type="text"
                ></opensilex-TagInputForm>
              </opensilex-FilterField>

            

              <!-- Provenance -->
              <opensilex-FilterField halfWidth="true" >
                <opensilex-DataProvenanceSelector
                  ref="provSelector"
                  :provenances.sync="filter.provenance"
                  label="ExperimentData.provenance"
                  @select="loadProvenance"
                  :experiments="[uri]"
                  :targets="filter.scientificObjects"
                  :multiple="false"
                  :viewHandler="showProvenanceDetails"
                  :viewHandlerDetailsVisible="visibleDetails"
                  :key="refreshKey"
                ></opensilex-DataProvenanceSelector>

                <b-collapse
                  v-if="selectedProvenance"
                  id="collapse-4"
                  v-model="visibleDetails"
                  class="mt-2"
                >
                  <opensilex-ProvenanceDetails
                    :provenance="getSelectedProv"
                  ></opensilex-ProvenanceDetails>
                </b-collapse>
              </opensilex-FilterField>
            </template>
          </opensilex-SearchFilterField>

          <opensilex-DataList ref="dataList" :listFilter.sync="filter">
          </opensilex-DataList>

        </template>
      </opensilex-Card>
    </div>

    <opensilex-ModalForm
      ref="dataForm"
      :initForm="initFormData"
      createTitle="DataImportForm.create"
      editTitle="DataImportForm.update"
      component="opensilex-DataImportForm"
      icon="ik#ik-bar-chart-line"
      modalSize="xl"
      @onCreate="afterCreateData"
      :successMessage="successMessage"
    ></opensilex-ModalForm>

    <opensilex-ResultModalView
      ref="resultModal"
      @onHide="refreshDataAfterImportation()"
    >
    </opensilex-ResultModalView>
  </div>
</template>

<script lang="ts">
import { Component, Ref } from "vue-property-decorator";
import Vue from "vue";
// @ts-ignore
import { ProvenanceGetDTO } from "opensilex-core/index";
// @ts-ignore
import HttpResponse, { OpenSilexResponse } from "opensilex-core/HttpResponse";
import { ScientificObjectNodeDTO } from "opensilex-core/model/scientificObjectNodeDTO";

@Component
export default class ExperimentData extends Vue {
  $opensilex: any;
  $t: any;
  $route: any;
  uri = null;
  visibleDetails: boolean = false;
  searchVisible: boolean = false;
  usedVariables: any[] = [];
  selectedProvenance: any = null;
  refreshKey = 0;

  filter = {
    start_date: null,
    end_date: null,
    provenance: null,
    variables: [],
    experiments: [this.uri],
    scientificObjects: [],
    targets: []
  };

  soFilter = {
    name: "",
    experiment: this.uri,
    germplasm: undefined,
    factorLevels: [],
    types: [],
    existenceDate: undefined,
    creationDate: undefined,
  };

  @Ref("dataList") readonly dataList!: any;
  @Ref("dataForm") readonly dataForm!: any;
  @Ref("searchField") readonly searchField!: any;
  @Ref("provSelector") readonly provSelector!: any;
  @Ref("resultModal") readonly resultModal!: any;
  @Ref("soSelector") readonly soSelector!: any;

  get credentials() {
    return this.$store.state.credentials;
  }

  get user() {
    return this.$store.state.user;
  }

  refreshProvComponent() {
    this.refreshKey += 1;
  }

  created() {
    this.uri = decodeURIComponent(this.$route.params.uri);
    this.resetFilters();

    this.soFilter = {
      name: "",
      experiment: this.uri,
      germplasm: undefined,
      factorLevels: [],
      types: [],
      existenceDate: undefined,
      creationDate: undefined,
    };
  }

  resetFilters() {
    this.filter = {
      start_date: null,
      end_date: null,
      provenance: null,
      variables: [],
      experiments: [this.uri],
      scientificObjects: [],
      targets: []
    };
    // Only if search and reset button are use in list
  }

  refreshSoSelector() {
    
    this.soSelector.refreshModalSearch();
    this.refreshProvComponent();
  }

  successMessage(form) {
    return this.$t("ResultModalView.data-imported");
  }

  get getSelectedProv() {
    return this.selectedProvenance;
  }

  refreshDataAfterImportation() {
    this.loadProvenance({ id: this.filter.provenance });
    this.refresh();
  }

  afterCreateData(results) {
    if (results instanceof Promise) {
      results.then((res) => {
        this.resultModal.setNbLinesImported(
          res.validation.dataErrors.nbLinesImported
        );
        this.resultModal.setProvenance(res.form.provenance);
        this.resultModal.show();
        this.clear();
        this.filter.provenance = res.form.provenance.uri;
        this.refreshVariables();
        this.refreshKey += 1;
        this.loadProvenance({ id: res.form.provenance.uri });
      });
    } else {
      this.resultModal.setNbLinesImported(
        results.validation.dataErrors.nbLinesImported
      );
      this.resultModal.setProvenance(results.form.provenance);
      this.resultModal.show();
      this.clear();
      this.filter.provenance = results.form.provenance.uri;
      this.refreshVariables();
      this.refreshKey += 1;
      this.loadProvenance({ id: results.form.provenance.uri });
    }
  }

  initFormData(form) {
    form.experiment = this.uri;
    return form;
  }

  showProvenanceDetails() {
    if (this.selectedProvenance != null) {
      this.visibleDetails = !this.visibleDetails;
    }
  }

  clear() {
    this.searchVisible = false;
    this.selectedProvenance = null;
    this.resetFilters();
    this.refresh();
  }

  mounted() {
    this.searchVisible = false;
    this.refreshVariables();
  }

  refreshVariables() {
    this.$opensilex
      .getService("opensilex.DataService")
      .getUsedVariables([this.uri], null, null, null)
      .then((http) => {
        let variables = http.response.result;
        this.usedVariables = [];
        for (let i in variables) {
          let variable = variables[i];
          this.usedVariables.push({
            id: variable.uri,
            label: variable.name,
          });
        }
      });
  }

  getProvenance(uri) {
    if (uri != undefined && uri != null) {
      return this.$opensilex
        .getService("opensilex.DataService")
        .getProvenance(uri)
        .then((http: HttpResponse<OpenSilexResponse<ProvenanceGetDTO>>) => {
          return http.response.result;
        });
    }
  }

  loadProvenance(selectedValue) {
    if (selectedValue != undefined && selectedValue != null) {
      this.getProvenance(selectedValue.id).then((prov) => {
        this.selectedProvenance = prov;
      });
    }
  }

  refresh() {
    this.searchVisible = true;
    this.dataList.refresh();
    //remove experiments filter from URL
    this.$nextTick(() => {
      this.$opensilex.updateURLParameter("experiments", null, "");
    });
  }

}
</script>

<style scoped lang="scss">
</style>

<i18n>
en:
    ExperimentData:
        object: Scientific Object
        date: Date
        value: Value
        variable : Variable
        provenance: Provenance
        export : Export
        export-wide : Wide format
        export-wide-help : A given date, provenance, scientific object of an observation represents a row and each variable value is in a specific column.
        export-long : Long format
        export-long-help : Each line represent an observation (Same as the result table)
fr:
    ExperimentData:
        object: Objet Scientifique
        date: Date
        value: Valeur
        variable : Variable
        provenance: Provenance
        export : Exporter
        export-wide : Format large
        export-wide-help : Une date, une provenance, un objet scientifique donné d'une observation représente une ligne et chaque valeur de variable est dans une colonne spécifique.
        export-long : Format long
        export-long-help : Une ligne représente une observation (identique au tableau de résultat)

</i18n>
