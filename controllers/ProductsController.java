package com.wp.web.controllers;

import com.wp.crypto.Hash;
import com.wp.model.PageCity;
import com.wp.model.Product;
import com.wp.model.ProductGroupProperties;
import com.wp.model.composite.Crumb;
import com.wp.model.enums.TypesEnum;
import com.wp.model.objects.Nodes;
import com.wp.servicies.interfaces.*;
import com.wp.utils.Cast;
import com.wp.utils.Is;
import com.wp.utils.mybatis.plugins.paging.page.PageContext;
import com.wp.web.annotation.aspect.*;
import com.wp.web.enums.CompanyNavigationEnum;
import com.wp.web.enums.PageCityNavigationEnum;
import com.wp.web.exceptions.BadRequestException;
import com.wp.web.exceptions.NotFoundException;
import com.wp.web.forms.ProductForm;
import com.wp.web.utils.CookieUtils;
import com.wp.web.utils.R;
import com.wp.web.views.*;
import com.wp.web.views.company.CompanyView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.*;

/**
 * Данный класс представляет из себя контроллер для работы с товарами
 *
 * @author Ivan Yevsyukov
 * @e-mail ivan_yevsyukov@mail.ru
 */
@Controller
public class ProductsController extends AController {

    // <editor-fold defaultstate="collapsed" desc="Подключаемые сервисы">
    // Сервис для работы с разделами
    @Autowired
    ISectionsAndFiltersService _sectionsService;

    // Сервис для работы с товарами
    @Autowired
    IProductsService _productsService;

    // Сервис для работы со страницами компаний
    @Autowired
    ICompanyService _companyService;

    // Сервис для работы с пользователем
    @Autowired
    IUserService _userService;
    // </editor-fold>

    // Главный раздел для товаров (товары хранятся в виде дерева и для того, чтобы получить разделы верхнего
    // уровня, нам необходимо знать главный раздел)
    @Value("${products_section}")
    protected long MAIN_PRODUCT_SECTION_ID = 0;

    // <editor-fold defaultstate="collapsed" desc="Список опубликованных товаров">
    /**
     * Получаем список всех опубликованных товаров
     *
     * @param model модель данных для шаблона
     * @param request данные о запросе
     * @return возвращает страницу с товарами
     */
    @PageCityNavigation(sections = PageCityNavigationEnum.PRODUCTS)
    @RequestMapping(value = "/products", method = RequestMethod.GET)
    public String getProducts(ModelMap model, HttpServletRequest request) {
        return getProducts(model, request, null);
    }

    /**
     * Получаем список всех опубликованных товаров в определенном разделе
     *
     * @param model модель данных для шаблона
     * @param request данные о запросе
     * @param pathSection строка с названием раздела (англоязычное название)
     * @return возвращает страницу с товарами для конкретного раздела
     */
    @PageCityNavigation(sections = PageCityNavigationEnum.PRODUCTS)
    @RequestMapping(value = "/products/{pathSection}", method = RequestMethod.GET)
    public String getProducts(ModelMap model, HttpServletRequest request, @PathVariable String pathSection) {

        // Получаем поддомен сайта, который является именем узла для страницы городов (method )
        PageCity pageCity = getCurentDomainLocation();

        if (!Is.Empty(pageCity) && !Is.Empty(pageCity.getId())) {
            return getProductsForPublic(pageCity, model, request, pathSection);
        }

        return getProductsForPublic(null, model, request, pathSection);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Список опубликованных товаров для конкретного узла">
    /**
     * Получаем список всех опубликованных товаров для конкретного узла компании
     *
     * @param model модель данных для шаблона
     * @param request данные о запросе
     * @param alias название узла для компании
     * @return возвращает страницу с товарами для конкретного узла
     */
    @CompanyNavigation(sections = {CompanyNavigationEnum.PRODUCTS, CompanyNavigationEnum.SHOP})
    @RequestMapping(value = "/{alias}/products", method = RequestMethod.GET)
    public String getProductsByNode(ModelMap model, HttpServletRequest request, @PathVariable @NodeName String alias) {
        return getProductsByNode(model, request, alias, null);
    }


    /**
     * Получаем список всех опубликованных товаров для конкретного узла компании в конкретном разделе
     *
     * @param model модель данных для шаблона
     * @param request данные о запросе
     * @param alias название узла для компании
     * @param pathSection строка с названием раздела (англоязычное название)
     * @return возвращает страницу с товарами для конкретного узла в конкретном разделе
     */
    @CompanyNavigation(sections = {CompanyNavigationEnum.PRODUCTS, CompanyNavigationEnum.SHOP})
    @RequestMapping(value = "/{alias}/products/{pathSection}", method = RequestMethod.GET)
    public String getProductsByNode(ModelMap model, HttpServletRequest request, @PathVariable @NodeName String alias,
                                    @PathVariable String pathSection) {

        // Получаем имя узла по его имени (alias - англоязычное имя)
        Nodes node = getNodeByAlias(alias);

        // Данный обработчик работает только с узлами компании, поэтому мы другие не обрабатываем
        if (!node.getType().equals(TypesEnum.COMPANY.getType())) {
            throw new NotFoundException("Страница не найдена.");
        }

        // Получаем информацию о пользователе, который зашел на страницу (владелец он или нет)
        boolean is_owner = (boolean) model.get("is_owner");

        // Параметр, отвечающий за то, хотим мы получать опубликованные товары или нам нет разницы (если на страницу
        // зашел владелец, то соответственно нужно показать все товары, для гостя показываем только опубликованные)
        Long is_active = is_owner ? null : 1l;

        // Получаем идентификатор раздела из параметров запроса
        long sectionId = _sectionsService.getSectionFromRequest(model, pathSection, MAIN_PRODUCT_SECTION_ID);

        // Получаем фильтры из параметров запроса
        Map<String, String[]> filters_from_url = getFiltersFromRequest(request);

        // Так как мы работаем со списком товаров на клиенте у нас имеется постраничная навигация, тем самым
        // товары мы выводим группами => узнаем номер текущей страницы
        int page = Cast.toInt(request.getParameter("page"));
        if (page == 0) {
            page = 1;
        }

        // Формируем параметры запроса для постраничной навигации
        PageContext pc = new PageContext().setPageSize(30).setCurrentPage(page);

        // Получаем список товаров для конкретного идентификатора узла, раздела, фильтров
        List<ProductViewForList> products =
                _productsService.getProductsByNodeId(node.getId(), is_active, sectionId, pc, filters_from_url);

        // Помечаем товары, которые уже есть в корзине у пользователя (не важно владелец он или нет)
        _productsService.setInCartForProductViews(products, user.getFirstNode());

        // Записываем в модель данные, которые нужно для html страницы
        model.put("products", products);
        model.put("page", pc);
        model.put("sections", getSectionsForShop(node, sectionId, is_owner));
        model.put("filters", getFiltersForShop(sectionId, filters_from_url));

        // Возвращаем нужный шаблон для данного url
        return R.COMPANY_PRODUCTS_HOLDER;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем все похожие товары по характеристикам">
    /**
     * Получаем список всех товаров, схожих по группе (например, модель телефона с разными характеристиками)
     *
     * @param model модель данных для шаблона
     * @param request данные о запросе
     * @param groupId идентификатор группы товаров
     * @return возвращает список всех товаров, схожих по группе
     */
    @CompanyNavigation(sections = {CompanyNavigationEnum.PRODUCTS, CompanyNavigationEnum.SHOP})
    @RequestMapping(value = "/group/{groupId}", method = RequestMethod.GET)
    public String getProductsConfigurations(ModelMap model, HttpServletRequest request,
                                            @PathVariable @ObjectId String groupId) {

        long group_id = Cast.toLong(groupId);

        // Получаем все конфигурации товара (например, все телефоны одной марки и модели, у которых тактовая частота
        // процессора отличается => соответственно и отличается цена)
        List<ProductViewForEdit> product_configurations = _productsService.getProductsByGroupIdForEdit(group_id);

        if (product_configurations.isEmpty()) {
            throw new NotFoundException("Конфигурации не найдены");
        }

        // Получаем первый товар в конфигурации
        ProductViewForEdit fp = product_configurations.get(0);

        // Узнаем раздел, в котором лежат все товары одной группы
        Long section_id = fp.getSection_id();

        // Получаем фильтры из параметров запроса
        Map<String, String[]> filters_from_url = getFiltersFromRequest(request);

        // Получаем все характеристики группы товаров
        ProductGroupProperties group_properties = _productsService.getGroupPropertiesModel(group_id);

        // Формируем параметры запроса для постраничной навигации
        PageContext pc = new PageContext().setPageSize(30).setCurrentPage(1);

        // Если мы работаем с разделом одежды, получаем систему измерения размеров для группы товаров
        String dimension_system =   Is.Empty(request.getParameter("dimension_system")) ?
                                        group_properties.getDimension_system() :
                                        request.getParameter("dimension_system");

        // Получаем выбранные фильтры на странице поиска товаров по параметрам (аналог Яндекс Маркета)
        // http://market.yandex.ru/guru.xml?CMD=-RR%3D9%2C0%2C0%2C0-VIS%3D8070-CAT_ID%3D160043-EXC%3D1-PG%3D10&hid=91491
        List<FilterView> filters = _sectionsService.getOnlySelectedFilters( section_id,
                                                                            group_properties.getFilters(),
                                                                            filters_from_url,
                                                                            dimension_system);

        // Если фильтры не были переданы или их воообще нет, то присваиваем переменной null для того, чтобы
        // не учитывать фильтры при выборе конфигураций
        if ((Is.Empty(filters) || filters.isEmpty()) && !product_configurations.isEmpty()) {
            filters_from_url = null;
        }

        // Получаем найденные конфигурации согласно выбранным фильтрам
        model.put("product_configurations",
                _productsService.getProductsByGroupIdForList(group_id, fp.getSection_id(), pc, filters_from_url));

        model.put("filters", filters);

        // Получаем информацию об усредненных данных о товаре, аналог Яндекс Маркета
        // http://market.yandex.ru/model.xml?modelid=11002813&hid=91491
        model.put("view", getProductAverage(product_configurations, filters));

        // Возвращаем нужный шаблон для данного url
        return R.PRODUCT_VIEW_HOLDER;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем подробное описание товара">
    /**
     * Получаем описание товара с его характеристиками
     * @param model модель данных для шаблона
     * @param productId идентификатор товара
     * @return возвращаем страницу с подробным описанием и характеристиками товара
     */
    @CompanyNavigation(sections = {CompanyNavigationEnum.PRODUCTS, CompanyNavigationEnum.SHOP})
    @RequestMapping(value = "/product/{productId}", method = RequestMethod.GET)
    public String getProductDetails(ModelMap model, @PathVariable @ObjectId String productId) {

        long product_id = Cast.toLong(productId);

        // Получаем данные о товаре
        ProductViewForView product = _productsService.getProductView(product_id);

        // Записываем их в модель для html-страницы
        model.put("view", product);

        // Возвращаем нужный шаблон для данного url
        return R.PRODUCT_VIEW_HOLDER;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем форму создания товара">
    /**
     * Получаем страницу с формой добавления товара
     *
     * @param model модель данных для шаблона
     * @param request данные о запросе
     * @param alias название узла для компании
     * @return страницу для редактирования объявления
     */
    @CompanyNavigation(sections = {CompanyNavigationEnum.PRODUCTS, CompanyNavigationEnum.SHOP})
    @RequestMapping(value = "/{alias}/product", method = RequestMethod.GET)
    public String getNewProductForm(ModelMap model, HttpServletRequest request, @PathVariable @NodeName String alias) {

        // Получаем имя узла по его имени (alias - англоязычное имя)
        Nodes node = getNodeByAlias(alias);

        // Проверяем права доступа пользователя к данной странице
        _userService.checkUser(user, node, model);

        // Возвращаем нужный шаблон для данного url
        return R.COMPANY_PRODUCTS_HOLDER;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Добавляем новый товар">
    /**
     * Добавляем новый товар на узел
     * @param model модель данных для шаблона
     * @param alias название узла
     * @param form форма с данными, которые необходимы для сохранения товара
     * @param result параметр, говорящий о достоверности данных, переданных с клиента
     * @return возвращаем шаблон, с сохраненным товаром
     */
    @RequestMapping(value = "/{alias}/product", method = RequestMethod.POST)
    public String addProduct(ModelMap model, @PathVariable String alias, @Valid ProductForm form, BindingResult result) {

        // Получаем имя узла по его имени (alias - англоязычное имя)
        Nodes node = getNodeByAlias(alias);

        // Проверяем права доступа пользователя к данной странице
        _userService.checkUser(user, node, model);

        // Проверяем данные на достоверность перед дальнейшими действиями (как известно, нельзя доверять данным,
        // передаваемых с клиента. Злоумышленних может имитировать запрос на сервер, этими проверками мы исключаем
        // запись в БД недостоверных данных)
        _messageResolver.resolveBindingResult(result);

        if (form.getGroup_id() != null && form.getGroup_id() > 0) {
            // Проверяем уникальность конфигурации
            if ((Is.Empty(form.getCopy_this()) || form.getCopy_this() == 0) &&
                !_productsService.checkConfigurationForUnique(form.getGroup_id(), 0l, form.getFilters(), node)) {
                model.put("warning_type", "duplicate_configuration");

                // Возвращаем пользователю предупреждение о том, что данная конфигурация уже существует
                return R.SAVED_PRODUCT_RESULT;
            }
        }
        else if (!Is.Empty(form.getFirst_object_id()) && form.getFirst_object_id() > 0) {
            // Если добавляется конфигурация к товару, то проверям есть ли товар, к которому она добавляется
            // (внутренний метод выбросет исключение в случае если товар не будет найден в БД)
            Product product = _productsService.getProductModel(form.getFirst_object_id());

            // Проверяем уникальность конфигурации
            if ((   Is.Empty(form.getCopy_this()) || form.getCopy_this() == 0) &&
                    product.getHash().equals(Hash.getHex(form.getFilters()))) {

                model.put("warning_type", "duplicate_configuration");

                // Возвращаем пользователю предупреждение о том, что данная конфигурация уже существует
                return R.SAVED_PRODUCT_RESULT;
            }

            // Если это первая добавленная конфигурация к товару, то создаем папку в которых будут храниться
            // все последующие конфигурация, включая эти 2
            form.setGroup_id(_productsService.createFolderForProduct(form.getFirst_object_id()));
        }

        // Обновляем количество новых товаров у компании (после этого действия пользователи, зашедшие на страницу
        // компании увидят, что компания добавила новые товары) - аналог уведомлений в ВК
        if (_userNavigation != null) {
            _userNavigation.saveOrUpdate(node, CompanyNavigationEnum.PRODUCTS.getNavigationsSection(), true);
        }

        // Делаем некоторые обработки с добавлением товара и возвращаем данные на клиент
        return processingProductForSave(node, 0l, form, model);

    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Обновляем товар">
    /**
     * Обновляем товар на узле компании
     * @param model модель данных для шаблона
     * @param productId идентификатор товара
     * @param form форма с данными, которые необходимы для сохранения товара
     * @param result параметр, говорящий о достоверности данных, переданных с клиента
     * @return возвращаем шаблон, с сохраненным товаром
     */
    @RequestMapping(value = "/product/{productId}", method = RequestMethod.POST)
    public String updateProduct(ModelMap model, @PathVariable String productId, @Valid ProductForm form, BindingResult result) {

        long product_id = Cast.toLong(productId);

        // Получаем имя узла по его имени (alias - англоязычное имя)
        Nodes node = _userService.getNodeByObjectId(product_id);

        // Проверяем права доступа пользователя к данной странице
        _userService.checkUser(user, node, model);

        // Проверяем данные на достоверность перед дальнейшими действиями (как известно, нельзя доверять данным,
        // передаваемых с клиента. Злоумышленних может имитировать запрос на сервер, этими проверками мы исключаем
        // запись в БД недостоверных данных)
        _messageResolver.resolveBindingResult(result);

        if (form.getGroup_id() != null && form.getGroup_id() > 0) {
            // Проверяем если ли вообше такой товар заодно и унаем раздел товара, который мы обновляем
            Product product = _productsService.getProductModel(product_id);

            if (product.getSection_id() != form.getSection_id()) {
                throw new BadRequestException("Вы не можете сменить раздел у товара, который находится в конфигурации");
            }

            // Проверяем уникальность конфигурации
            if ((Is.Empty(form.getCopy_this()) || form.getCopy_this() == 0) &&
                !_productsService.checkConfigurationForUnique(form.getGroup_id(), product_id, form.getFilters(), node)) {

                model.put("warning_type", "duplicate_configuration");

                // Возвращаем пользователю предупреждение о том, что данная конфигурация уже существует
                return R.SAVED_PRODUCT_RESULT;
            }
        }

        // Делаем некоторые обработки с добавлением товара и возвращаем данные на клиент
        return processingProductForSave(node, product_id, form, model);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Вспомогательные методы">
    private String processingProductForSave(Nodes node, Long product_id, ProductForm form, ModelMap model){

        // Сохранеяем товар
        product_id = _productsService.saveProduct(node.getId(), product_id, form.getGroup_id(), form);

        // Получаем данные об обновленном/добавленном товаре, для того, чтобы на странице заменить/добавить
        // его на странице
        ProductConfigurationsViewForList product = _productsService.getProductConfigurationForListItem(product_id);

        // Неинтересные обработки для того, чтобы узнать какой шаблон отдать на клиент
        processingReturnForms(product_id, model, form.getReturn_forms(), form.getGroup_id());

        model.put("configuration_item", product);

        // Возвращаем шаблон обновленного товара
        return R.SAVED_PRODUCT_RESULT;

    }

    private Map<String, String[]> getFiltersFromRequest(HttpServletRequest request) {

        Map<String, String[]> params = request.getParameterMap();
        Map<String, String[]> filters = new HashMap<String, String[]>();

        for (String key : params.keySet()) {
            filters.put(key, params.get(key));
        }

        return filters;
    }

    private List<FilterView> getFiltersForShop(long sectionId, Map<String, String[]> filters) {

        List<FilterView> filtersForSection;

        if (sectionId <= 0l) {
            return new ArrayList<FilterView>();
        }

        // Получаем фильтры для конкертного раздела
        filtersForSection = _sectionsService.getFiltersForSection(sectionId);

        // Оставляем только фильтры, участвующие в поиске
        for (int i = 0; i < filtersForSection.size(); i++) {
            if (filtersForSection.get(i).get_in_card() == 1) {
                filtersForSection.remove(i);
                i--;
            }
        }

        // Применяем установленные фильтры
        _sectionsService.setFiltersValues(filtersForSection, _sectionsService.getFiltersValues(filters));

        return filtersForSection;
    }

    private List<SectionView> getSectionsForShop(Nodes node, Long sectionId, boolean is_owner) {

        // Возмем разделы, в которых есть товары компании
        List<SectionView> sections = new ArrayList<SectionView>();
        if (sectionId == MAIN_PRODUCT_SECTION_ID) {
            boolean onlyPublished = !is_owner;

            // Получаем опубликованные разделы для магазина (компании)
            sections = _productsService.getSectionsForShop(node.getId(), onlyPublished);

            // Неинтересные преобразования
            for (int i = 0, l = sections.size(); i < l; i++) {
                List<Crumb> crumbs = _sectionsService.getBreadcrumbsForSection(MAIN_PRODUCT_SECTION_ID, sections.get(i).getId());
                // из бредкрамсов узнаем полные пути к разделам
                String path_name = "";
                String separator = "";
                for (Crumb crumb : crumbs) {
                    path_name += separator + crumb.getAlias();
                    separator = "-";
                }
                sections.get(i).setPath_name(path_name);
            }
        }

        return sections;

    }

    private void processingReturnForms(Long product_id, ModelMap model, String return_forms_string, Long group_id) {

        if (!Is.Empty(return_forms_string) && !return_forms_string.trim().equals("")) {
            List<String> return_forms = Arrays.asList(return_forms_string.split(","));

            for (String f : return_forms) {
                long productId = product_id == null ? 0 : product_id;

                if (productId == 0) {
                    //Если productId не передан, то возвращаем последний товар
                    if (group_id == null || group_id == 0) {
                        continue;
                    }

                    List<ProductConfigurationsViewForList> products_groups =
                            _productsService.getProductsByGroupIdForList(group_id, 0l, null, null);

                    if (products_groups.isEmpty()){
                        continue;
                    }

                    productId = products_groups.get(products_groups.size() - 1).getId();
                }

                if (productId == 0) {
                    continue;
                }

                if (f.equals("product_list_item")) {
                    model.put("item", _productsService.getProductForList(productId));
                }
                else if (f.equals("product_view")) {
                    model.put("view", _productsService.getProductView(productId));
                }
            }

            model.put("return_forms", return_forms);
        }

    }

    private ProductAverageView getProductAverage(List<ProductViewForEdit> products_configurations, List<FilterView> filters) {

        // Получаем первый товар в конфигурации
        ProductViewForEdit fp = products_configurations.get(0);

        List<FilterAverageView> filters_average = new ArrayList<FilterAverageView>();
        HashMap<Long, Integer> filters_keys_by_ids = new HashMap<Long, Integer>();
        Long price = 0l;

        // Заполняем фильтрами, которые указаны во всех конфигурациях (дублирующие пропускаем)
        for (ProductViewForEdit p : products_configurations) {
            price = _sectionsService.getFiltersForConfigurationView(p.getFilters(), filters_average, filters_keys_by_ids);
        }

        // Формируем данные для усредненного товара
        ProductAverageView product_average = new ProductAverageView(fp.getTitle(), fp.getDescription(), filters_average, price);

        String dimension = null;

        // Записываем все изображения конфигураций
        HashMap<Long, Boolean> hm = new HashMap<Long, Boolean>();
        for (ProductViewForEdit p : products_configurations) {
            if (Is.Empty(dimension) && !Is.Empty(p.getDimension())) {
                dimension = p.getDimension();
            }
            //Добавляем изображениея без дубликатов
            for (ImageView imageView : p.getImages()) {
                if (hm.containsKey(imageView.getId())) {
                    continue;
                }
                hm.put(imageView.getId(), true);
                product_average.getImages().add(imageView);
            }
        }

        if (!Is.Empty(dimension)) {
            product_average.setSearch_dimension(dimension);
        }

        // Добавляем значения, которые используются только в конфигурациях
        for (FilterView f : filters) {
            if (f.getAlias().equals("dimension") && !Is.Empty(f.get_dimension_system())) {
                dimension = f.get_dimension_system();
                product_average.setSearch_dimension(dimension);
            }

            if (f.getAlias().equals("price")) {
                product_average.setPrice(f.getMinValue());
                product_average.setPrice_to(f.getMaxValue());
                continue;
            }

            if (!filters_keys_by_ids.containsKey(f.getId()))
                continue;

            Integer key = filters_keys_by_ids.get(f.getId());

            if (f.getType().equals("number") || f.getType().equals("interval")) {
                product_average.getFilters().get(key).setValue(f.getMinValue());
                product_average.getFilters().get(key).setValue_to(f.getMaxValue());
            } else if (f.getType().equals("select")) {
                List<String> values = new ArrayList<String>();

                for (ListValue v : f.getValues()) {
                    values.add(v.getValue());
                }

                product_average.getFilters().get(key).setValues(values);
            }
        }

        // Устанавливаем интервал в фильтрах для конфигураций
        product_average.setFilters(filters_average);

        return product_average;
    }

    private String getProductsForPublic(PageCity pageCity, ModelMap model, HttpServletRequest request, String pathSection) {

        // Получаем идентификатор раздела из параметров запроса
        long sectionId = _sectionsService.getSectionFromRequest(model, pathSection, MAIN_PRODUCT_SECTION_ID);

        // Получаем фильтры из параметров запроса
        Map<String, String[]> filters_from_url = getFiltersFromRequest(request);

        Long location_id = 0l;
        int page = Cast.toInt(request.getParameter("page"));
        if (page == 0) {
            page = 1;
        }

        // Формируем параметры запроса для постраничной навигации
        PageContext pc = new PageContext().setPageSize(30).setCurrentPage(page);


        if (!Is.Empty(pageCity)) {
            location_id = pageCity.getLocation_id();
        }
        else {
            Cookie cookie = CookieUtils.getCookie(request, CookieUtils.CURRENT_LOCATION_ID);
            if (cookie != null && request.getCookies() != null) {
                location_id = Long.parseLong(cookie.getValue());
            }
        }

        // Получаем опубликованные товары, которые видны всем пользователям и посетителям сайта
        List<ProductViewForList> products = _productsService.getProductsForPublic(sectionId, location_id, null, pc, filters_from_url);

        // Устанавливаем значения для товаров, которые у пользователя в корзине
        _productsService.setInCartForProductViews(products, user.getFirstNode());

        // Записываем все разделы для товаров, для того, чтобы их показать в интерфейсе
        model.putAll(_sectionsService.sectionsAsModel(MAIN_PRODUCT_SECTION_ID, pathSection));

        model.put("products", products);
        model.put("page", pc.setCurrentPage(page));
        model.put("filters", getFiltersForShop(sectionId, filters_from_url));

        // Если имеем дело со страницей города, то возвращаем шаблон для страницы города с товарами
        if (!Is.Empty(pageCity)) {
            return R.COMPANY_PRODUCTS_HOLDER;
        }
        // Иначе возвращаем общий шаблон для опубликованных товаров
        else {
            return R.PUBLIC_PRODUCTS;
        }

    }
    // </editor-fold>
}
