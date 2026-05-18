package cn.cordys.common.service;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.util.OnceInterface;
import cn.cordys.common.util.OnceInterfaceAction;
import cn.cordys.crm.clue.service.ClueService;
import cn.cordys.crm.system.domain.ModuleField;
import cn.cordys.crm.system.domain.ModuleFieldBlob;
import cn.cordys.crm.system.domain.ModuleForm;
import cn.cordys.crm.system.domain.Parameter;
import cn.cordys.crm.system.service.ModuleFieldExtService;
import cn.cordys.crm.system.service.ModuleFieldService;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.crm.system.service.ModuleService;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import cn.cordys.uid.IDGenerator;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DataInitService {
    @Resource
    private ModuleService moduleService;
    @Resource
    private ModuleFormService moduleFormService;
    @Resource
    private ModuleFieldExtService moduleFieldExtService;
    @Resource
    private BaseMapper<Parameter> parameterMapper;
    @Resource
    private Redisson redisson;
    @Resource
    private ModuleFieldService moduleFieldService;
    @Resource
    private BaseMapper<ModuleForm> moduleFormMapper;
    @Resource
    private BaseMapper<ModuleField> moduleFieldMapper;
    @Resource
    private BaseMapper<ModuleFieldBlob> moduleFieldBlobMapper;
    @Resource
    private ClueService clueService;

    public void initOneTime() {
        RLock lock = redisson.getLock("init_data_lock");
        lock.lock();
        try {
            initOneTime(moduleService::initDefaultOrgModule, "init.module");
            initOneTime(moduleFormService::initForm, "init.form");
            initOneTime(moduleFieldService::modifyDateProp, "modify.form.date");
            initOneTime(moduleFormService::modifyFormLinkProp, "modify.form.link");
            initOneTime(moduleFormService::modifyFormProp, "modify.form.prop");
            initOneTime(moduleFormService::modifyFieldMobile, "modify.field.mobile");
            initOneTime(moduleFormService::processOldLinkData, "process.old.link.data");
            initOneTime(moduleFormService::initFormScenarioProp, "init.record.form.scenario");
            initOneTime(clueService::processTransferredCluePlanAndRecord, "process.transferred.clue");
            initOneTime(moduleFormService::initUpgradeForm, "init.upgrade.form.v1.4.0");
            initOneTime(moduleFormService::initUpgradeForm, "init.upgrade.form.v1.5.0");
            initOneTime(moduleFormService::initUpgradeForm, "init.upgrade.form.v1.5.1");
            initOneTime(moduleFormService::initExtFieldsByVer, "1.5.0", "init.ext.fields.v1.5.0");
            initOneTime(moduleFormService::initExtFieldsByVer, "1.5.1", "init.ext.fields.v1.5.1");
            initOneTime(this::initCustomerCountryField, "init.customer.country.field.v1.7.2");
            initOneTime(moduleFieldExtService::setDefaultOptionSource, "set.default.option.source");
            initOneTime(moduleFieldExtService::refreshPlanFieldPos, "refresh.plan.field.pos");
            initOneTime(moduleFormService::initInvoiceFormScenarioProp, "init.invoice.form.scenario");
            initOneTime(moduleFieldService::modifyInvoiceShowFields, "init.invoice.show.fields");
            initOneTime(moduleService::deleteExtraModules, "delete.extra.modules");
            initOneTime(moduleFieldExtService::modifySubProductSumColumn, "modify.quotation.product.sum.column");
            initOneTime(moduleFormService::initUpgradeForm, "init.upgrade.form.v1.6.0");
            initOneTime(moduleFieldService::initOrderFields, "init.order.fields");
            initOneTime(moduleFormService::initContactFormLinkRules, "init.contact.form.link.rules");
            initOneTime(moduleFormService::initContractToOrderLinkScenario, "init.order.form.link.rules");
            initOneTime(moduleFormService::initOrderFormScenarioProp, "init.order.form.scenario");
            initOneTime(moduleFieldExtService::modifyInternalSubSumColumn, "modify.internal.sum.column");
            initOneTime(moduleFieldExtService::modifyInternalSubCalcFormula, "modify.internal.calc.formula");
            initOneTime(moduleFieldExtService::refreshFormulaOldReferencedId, "refresh.formula.old.referenced.id");
        } finally {
            lock.unlock();
        }
    }

    private void initOneTime(OnceInterface onceFunc, final String key) {
        try {
            LambdaQueryWrapper<Parameter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Parameter::getParamKey, key);
            List<Parameter> parameters = parameterMapper.selectListByLambda(queryWrapper);
            if (CollectionUtils.isEmpty(parameters)) {
                onceFunc.execute();
                insertParameterOnceKey(key);
            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

    private <P> void initOneTime(OnceInterfaceAction<P> onceFunc, P param, final String key) {
        try {
            LambdaQueryWrapper<Parameter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Parameter::getParamKey, key);
            List<Parameter> parameters = parameterMapper.selectListByLambda(queryWrapper);
            if (CollectionUtils.isEmpty(parameters)) {
                onceFunc.execute(param);
                insertParameterOnceKey(key);
            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

    private void insertParameterOnceKey(String key) {
        Parameter parameter = new Parameter();
        parameter.setParamKey(key);
        parameter.setParamValue("done");
        parameter.setType("text");
        parameterMapper.insert(parameter);
    }

    private void initCustomerCountryField() {
        LambdaQueryWrapper<ModuleForm> formWrapper = new LambdaQueryWrapper<>();
        formWrapper.eq(ModuleForm::getFormKey, FormKey.CUSTOMER.getKey());
        List<ModuleForm> forms = moduleFormMapper.selectListByLambda(formWrapper);
        for (ModuleForm form : forms) {
            LambdaQueryWrapper<ModuleField> countryWrapper = new LambdaQueryWrapper<>();
            countryWrapper.eq(ModuleField::getFormId, form.getId())
                    .eq(ModuleField::getInternalKey, "country");
            if (CollectionUtils.isNotEmpty(moduleFieldMapper.selectListByLambda(countryWrapper))) {
                continue;
            }

            LambdaQueryWrapper<ModuleField> legacyWrapper = new LambdaQueryWrapper<>();
            legacyWrapper.eq(ModuleField::getFormId, form.getId())
                    .eq(ModuleField::getInternalKey, "customerCountry");
            List<ModuleField> legacyFields = moduleFieldMapper.selectListByLambda(legacyWrapper);
            if (CollectionUtils.isNotEmpty(legacyFields)) {
                ModuleField legacyField = legacyFields.getFirst();
                legacyField.setInternalKey("country");
                legacyField.setType("INPUT");
                legacyField.setMobile(true);
                legacyField.setUpdateUser("admin");
                legacyField.setUpdateTime(System.currentTimeMillis());
                moduleFieldMapper.updateById(legacyField);

                ModuleFieldBlob fieldBlob = new ModuleFieldBlob();
                fieldBlob.setId(legacyField.getId());
                fieldBlob.setProp(JSON.toJSONString(customerCountryFieldProp(legacyField.getId())));
                moduleFieldBlobMapper.updateById(fieldBlob);
                continue;
            }

            ModuleField field = new ModuleField();
            field.setId(IDGenerator.nextStr());
            field.setFormId(form.getId());
            field.setInternalKey("country");
            field.setName("\u56fd\u5bb6");
            field.setType("INPUT");
            field.setMobile(true);
            field.setPos(System.currentTimeMillis());
            field.setCreateUser("admin");
            field.setCreateTime(System.currentTimeMillis());
            field.setUpdateUser("admin");
            field.setUpdateTime(System.currentTimeMillis());
            moduleFieldMapper.insert(field);

            ModuleFieldBlob fieldBlob = new ModuleFieldBlob();
            fieldBlob.setId(field.getId());
            fieldBlob.setProp(JSON.toJSONString(customerCountryFieldProp(field.getId())));
            moduleFieldBlobMapper.insert(fieldBlob);
        }
    }

    private Map<String, Object> customerCountryFieldProp(String fieldId) {
        return Map.of(
                "id", fieldId,
                "name", "\u56fd\u5bb6",
                "internalKey", "country",
                "type", "INPUT",
                "showLabel", true,
                "readable", true,
                "editable", true,
                "fieldWidth", 1,
                "mobile", true
        );
    }
}
